package ke.nyalee.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.AlarmClock;
import android.provider.CallLog;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.telephony.SmsManager;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    // ---- colours ----
    static final int RED = 0xFFC62828, BLUE = 0xFF1565C0, GREEN = 0xFF2E7D32, ORANGE = 0xFFEF6C00,
            PURPLE = 0xFF6A1B9A, TEAL = 0xFF00897B, GREY = 0xFF455A64, YELLOW = 0xFFFFC107,
            PINK = 0xFFAD1457, CYAN = 0xFF00ACC1, INDIGO = 0xFF3949AB, VIOLET = 0xFF8E24AA,
            SKY = 0xFF1976D2, WAGREEN = 0xFF128C7E, MPESA = 0xFF43A047, DEEP = 0xFFD84315,
            LIME = 0xFF558B2F;
    static final int[] FAM_COLORS = {0xFFE91E63, 0xFF00ACC1, 0xFFFB8C00, 0xFF7CB342};
    static final int[] TITLE_COLORS = {0xFFC62828, 0xFFEF6C00, 0xFF2E7D32, 0xFF1565C0, 0xFF6A1B9A, 0xFFAD1457};

    static class Item {
        String sw = "", en = "", keys = "", icon = "";
        int color;
        boolean half, header;
        Runnable run;
    }

    SharedPreferences prefs;
    TextToSpeech tts;
    boolean ttsReady, torchOn;
    String torchId, currentPage = "home";
    int pendingAction = 0;      // 0 = SOS, 1 = "I'm OK" (used after the permission dialog)
    long lastTap = 0;
    final List<Item> home = new ArrayList<>(), tools = new ArrayList<>(), settings = new ArrayList<>(),
            help = new ArrayList<>(), nums = new ArrayList<>(), voiceOnly = new ArrayList<>();
    Item volUp, volDown;

    // ================= item builders =================

    Item tile(String sw, String en, int color, boolean half, String keys, Runnable r) {
        Item i = new Item();
        i.sw = sw; i.en = en; i.color = color; i.half = half; i.keys = keys; i.run = r;
        return i;
    }

    Item ic(Item i, String emoji) { i.icon = emoji; return i; }

    Item header(String t, int color) {
        Item i = new Item();
        i.header = true; i.sw = t; i.color = color;
        return i;
    }

    Item setting(String sw, int color, String icon, String keys, final String action) {
        return ic(tile(sw, "", color, true, keys, () -> open(new Intent(action))), icon);
    }

    Item dial(String sw, String en, int color, String icon, final String num, String keys) {
        return ic(tile(sw, en, color, false, keys,
                () -> open(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num))))), icon);
    }

    Item guide(final String sw, int color, String icon, final String text, String keys) {
        return ic(tile(sw, "", color, false, keys, () -> showGuide(sw, text)), icon);
    }

    // ================= lifecycle =================

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("nyalee", MODE_PRIVATE);
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.ENGLISH);
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.getDefault());
                }
                ttsReady = true;
            }
        });
        buildItems();
    }

    @Override
    protected void onResume() {
        super.onResume();
        show(currentPage);   // refreshes clock, battery, greeting and family tiles
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    // ================= content =================

    void buildItems() {
        volUp = ic(tile("Volume Up", "", GREEN, true, "volume up,louder,increase volume,sound up",
                () -> volume(AudioManager.ADJUST_RAISE)), "🔊");
        volDown = ic(tile("Volume Down", "", INDIGO, true, "volume down,quieter,decrease volume,sound down",
                () -> volume(AudioManager.ADJUST_LOWER)), "🔉");

        // ---------- MORE TOOLS ----------
        tools.add(ic(tile("Recent Calls", "Missed & received", RED, true, "recent calls,missed calls,call history,call log",
                this::recentCalls), "🕘"));
        tools.add(volUp);
        tools.add(volDown);
        tools.add(ic(tile("Weather", "Needs internet", SKY, true, "weather,rain,forecast",
                () -> open(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=weather+today")))), "⛅"));
        tools.add(ic(tile("Clock / Alarm", "", ORANGE, true, "alarm,clock",
                () -> open(new Intent(AlarmClock.ACTION_SHOW_ALARMS))), "⏰"));
        tools.add(ic(tile("Calendar", "", PURPLE, true, "calendar",
                () -> open(selector(Intent.CATEGORY_APP_CALENDAR))), "📅"));
        tools.add(ic(tile("Calculator", "", PINK, true, "calculator",
                () -> open(selector(Intent.CATEGORY_APP_CALCULATOR))), "🧮"));
        tools.add(ic(tile("Maps", "", GREEN, true, "maps,directions",
                () -> open(selector(Intent.CATEGORY_APP_MAPS))), "🗺️"));
        tools.add(ic(tile("Internet", "", INDIGO, true, "internet,browser,web",
                () -> open(selector(Intent.CATEGORY_APP_BROWSER))), "🌐"));
        tools.add(ic(tile("Email", "", DEEP, true, "email,gmail,mail",
                () -> open(selector(Intent.CATEGORY_APP_EMAIL))), "✉️"));
        tools.add(ic(tile("Facebook", "", BLUE, true, "facebook",
                () -> app("com.facebook.katana")), "👍"));
        tools.add(ic(tile("Music / Radio", "", VIOLET, true, "music,radio,songs",
                () -> open(selector(Intent.CATEGORY_APP_MUSIC))), "🎵"));
        tools.add(ic(tile("Torch", "", YELLOW, true, "turn on torch,torch light", this::toggleTorch), "🔦"));

        // ---------- IMPORTANT NUMBERS ----------
        nums.add(dial("Police / Emergency 999", "", RED, "🚓", "999", "police,999"));
        nums.add(dial("Emergency 112", "", DEEP, "🚨", "112", "emergency 112,112"));
        nums.add(dial("Red Cross 1199", "", PINK, "🚑", "1199", "red cross,ambulance,1199"));
        nums.add(dial("Customer Care 100", "Safaricom", GREEN, "☎️", "100", "customer care"));

        // ---------- SETTINGS ----------
        settings.add(header("Network", SKY));
        settings.add(setting("Wi-Fi", SKY, "📶", "wifi,wi-fi,wi fi", Settings.ACTION_WIFI_SETTINGS));
        settings.add(setting("Mobile Data", INDIGO, "📡", "mobile data,data,bundles", Settings.ACTION_NETWORK_OPERATOR_SETTINGS));
        settings.add(setting("Bluetooth", SKY, "🎧", "bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS));
        settings.add(setting("Airplane Mode", INDIGO, "✈️", "airplane mode,flight mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS));
        settings.add(setting("Data Usage", SKY, "📊", "data usage,bundles used", "android.settings.DATA_USAGE_SETTINGS"));
        settings.add(setting("All Networks", INDIGO, "🌍", "networks,hotspot,network settings", Settings.ACTION_WIRELESS_SETTINGS));
        settings.add(header("Sound & Screen", VIOLET));
        settings.add(volUp);
        settings.add(volDown);
        settings.add(setting("Ringtone & Sound", VIOLET, "🔔", "ringtone,sound settings", Settings.ACTION_SOUND_SETTINGS));
        settings.add(setting("Brightness / Font Size", PINK, "🔆", "brightness,font size,screen", Settings.ACTION_DISPLAY_SETTINGS));
        settings.add(setting("Night Light", PURPLE, "🌙", "night light", Settings.ACTION_NIGHT_DISPLAY_SETTINGS));
        settings.add(ic(tile("Text Size", "Bigger / smaller", CYAN, true,
                "text size,bigger text,larger text,make it bigger,make text bigger", this::cycleTextSize), "🔠"));
        settings.add(header("My Phone", TEAL));
        settings.add(setting("Battery", GREEN, "🔋", "battery settings,charge", Settings.ACTION_BATTERY_SAVER_SETTINGS));
        settings.add(setting("Storage", TEAL, "💾", "storage,space", Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        settings.add(setting("Date & Time", ORANGE, "🕒", "date and time,set time", Settings.ACTION_DATE_SETTINGS));
        settings.add(setting("Language", DEEP, "🗣️", "language", Settings.ACTION_LOCALE_SETTINGS));
        settings.add(setting("Keyboard", LIME, "⌨️", "keyboard,typing", Settings.ACTION_INPUT_METHOD_SETTINGS));
        settings.add(setting("Accessibility", CYAN, "♿", "accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS));
        settings.add(setting("Location", RED, "📍", "location,gps", Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        settings.add(setting("Update", MPESA, "⬆️", "update,software update", "android.settings.SYSTEM_UPDATE_SETTINGS"));
        settings.add(setting("About Phone", GREY, "ℹ️", "about phone", Settings.ACTION_DEVICE_INFO_SETTINGS));
        settings.add(header("More - for the caregiver", GREY));
        settings.add(setting("Security", GREY, "🔒", "security,lock screen", Settings.ACTION_SECURITY_SETTINGS));
        settings.add(setting("Accounts", GREY, "👤", "accounts,google account", Settings.ACTION_SYNC_SETTINGS));
        settings.add(setting("Apps", GREY, "📲", "apps,applications", Settings.ACTION_APPLICATION_SETTINGS));
        settings.add(ic(tile("Play Store", "Install apps", GREY, true, "play store,install app,download app",
                () -> app("com.android.vending")), "🛍️"));
        settings.add(setting("All Settings", GREY, "⚙️", "all settings", Settings.ACTION_SETTINGS));
        settings.add(setting("Set as Home Screen", GREY, "🏠", "home screen,set home", Settings.ACTION_HOME_SETTINGS));

        // ---------- VOICE HELP (the phone speaks; works fully offline) ----------
        help.add(guide("How to Make a Call", GREEN, "📞", "Press Contacts. Choose the person's name. Press the green phone button. When you finish, press the red button.", "how to call,how to make a call"));
        help.add(guide("How to Answer a Call", MPESA, "📲", "When the phone rings, drag the green button up, or press Answer. To end the call, press the red button.", "how to answer a call,how to answer"));
        help.add(guide("How to Send a WhatsApp", WAGREEN, "💬", "Press WhatsApp. Choose the person's name. Type your message at the bottom, then press the arrow to send. To call, press the phone picture at the top.", "how to whatsapp,how to send a whatsapp"));
        help.add(guide("How to Video Call", SKY, "🎥", "Press WhatsApp. Choose the person's name. Press the little camera picture at the top. Wait for them to answer.", "how to video call,video call"));
        help.add(guide("How to Read Messages", INDIGO, "✉️", "Press Messages. Press the name of the person to read their message. To reply, type at the bottom and press send.", "how to read messages,how to read a message"));
        help.add(guide("How to Take a Photo", VIOLET, "📷", "Press Camera. Press the big circle to take the photo. To see your photos, go back home and press Photos.", "how to take a photo,how to take photo"));
        help.add(guide("How to Turn On Wi-Fi", TEAL, "📶", "Press Settings, then Wi-Fi. Turn the switch on. Choose the network name and type the password.", "how to wifi,how to turn on wifi"));
        help.add(guide("No Internet", DEEP, "🚫", "Press Settings, then Wi-Fi, and make sure it is on. If you use mobile data, press Mobile Data and make sure it is on. Make sure Airplane Mode is off. If it still does not work, ask your family.", "no internet,internet is not working,internet not working"));
        help.add(guide("Sound Is Too Low", ORANGE, "🔊", "Press More Tools, then press Volume Up a few times.", "sound is too low,i cannot hear"));
        help.add(guide("Battery Is Low", CYAN, "🔋", "Plug the charger into the phone. Wait for the screen to light up. Do not remove the charger until the battery is full.", "battery is low,battery low"));
        help.add(guide("I Pressed Something By Mistake", PINK, "🙈", "Do not worry. Press the Back button to go back, or press the Home button to start again. Nothing bad has happened.", "pressed by mistake,i pressed something,i am lost"));
        help.add(guide("Phone Is Frozen", GREY, "❄️", "Press and hold the power button for about ten seconds until the phone restarts. If it still does not work, ask your family.", "phone is frozen,phone is stuck,phone is hanging"));
        help.add(guide("How to Ask for Help", RED, "🆘", "Press the red SOS button. Then press Yes. Your family will be called and sent a message with your location.", "how to ask for help"));
        help.add(guide("M-Pesa Safety", MPESA, "💰", "Press M-Pesa. Choose a service. Type your own PIN. Never give your PIN to anyone, even if they say they are from Safaricom.", "mpesa safety,m-pesa safety"));

        // ---------- VOICE-ONLY COMMANDS (no button) ----------
        voiceOnly.add(tile("", "", 0, false, "what time,what is the time,what day,what is the date,today's date,what is today",
                this::speakStatus));
        voiceOnly.add(tile("", "", 0, false, "battery level,how much battery,battery percentage,battery left",
                this::speakBattery));
        voiceOnly.add(tile("", "", 0, false, "go home,go back,take me home,main screen", () -> show("home")));
    }

    void buildHome() {
        home.clear();
        home.add(ic(tile("SOS - CALL FOR HELP", "", RED, false, "sos,help me,i need help,emergency help", this::sosConfirm), "🆘"));
        home.add(ic(tile("Talk (say what you want)", "", BLUE, false, "", this::listen), "🎤"));
        for (int n = 1; n <= 4; n++) {
            final String name = prefs.getString("fam" + n + "n", "");
            final String num = prefs.getString("fam" + n + "p", "");
            if (!name.isEmpty() && !num.isEmpty()) {
                home.add(ic(tile(name, "Call", FAM_COLORS[n - 1], true,
                        name.toLowerCase(Locale.ROOT), () -> callNumber(num, name)), "❤️"));
            }
        }
        home.add(ic(tile("I'm OK", "Tell my family", MPESA, true, "i am ok,i'm ok,i am fine,i'm fine,tell my family,send ok",
                this::okConfirm), "💚"));
        home.add(ic(tile("My Info", "For emergencies", LIME, true, "my info,my details,who am i,medical info",
                this::showInfo), "📋"));
        home.add(ic(tile("Phone", "", GREEN, true, "phone,call,dial,make a call",
                () -> open(new Intent(Intent.ACTION_DIAL))), "📞"));
        home.add(ic(tile("Messages", "", SKY, true, "messages,sms,text message",
                () -> open(selector(Intent.CATEGORY_APP_MESSAGING))), "✉️"));
        home.add(ic(tile("WhatsApp", "", WAGREEN, true, "whatsapp,whats app",
                () -> app("com.whatsapp", "com.whatsapp.w4b")), "💬"));
        home.add(ic(tile("YouTube", "", PINK, true, "youtube,you tube,videos",
                () -> app("com.google.android.youtube")), "▶️"));
        home.add(ic(tile("Photos", "", VIOLET, true, "photos,pictures,gallery",
                () -> open(selector(Intent.CATEGORY_APP_GALLERY))), "🖼️"));
        home.add(ic(tile("Camera", "", INDIGO, true, "camera,take a photo,take photo",
                () -> open(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))), "📷"));
        home.add(ic(tile("Contacts", "", ORANGE, true, "contacts,phone numbers,address book",
                () -> open(selector(Intent.CATEGORY_APP_CONTACTS))), "👥"));
        home.add(ic(tile("M-Pesa", "", MPESA, true, "mpesa,m-pesa,m pesa,money", this::mpesa), "💰"));
        home.add(ic(tile("Torch", "", YELLOW, true, "torch,flashlight,light", this::toggleTorch), "🔦"));
        home.add(ic(tile("Medicine", "Reminder", CYAN, true, "medicine,medication,pills,reminder", this::medicineReminder), "💊"));
        home.add(ic(tile("Emergency Numbers", "", DEEP, true, "emergency numbers,important numbers",
                () -> show("nums")), "🚑"));
        home.add(ic(tile("More Tools", "", PURPLE, true, "more tools,tools", () -> show("tools")), "🧰"));
        home.add(ic(tile("Settings", "", GREY, true, "settings", () -> show("settings")), "⚙️"));
        home.add(ic(tile("Voice Help", "How to...", TEAL, true, "voice help,help me learn,how to", () -> show("help")), "🗣️"));
    }

    // ================= UI =================

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    void show(String page) {
        currentPage = page;
        switch (page) {
            case "tools": render("More Tools", tools, false); break;
            case "settings": render("Settings", settings, false); break;
            case "help": render("Voice Help", help, false); break;
            case "nums": render("Emergency Numbers", nums, false); break;
            default:
                buildHome();
                render("Nyalee", home, true);
        }
    }

    CharSequence rainbow(String s) {
        SpannableString sp = new SpannableString(s);
        for (int i = 0; i < s.length(); i++) {
            sp.setSpan(new ForegroundColorSpan(TITLE_COLORS[i % TITLE_COLORS.length]), i, i + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sp;
    }

    String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g = h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
        String name = prefs.getString("info_name", "").trim();
        if (!name.isEmpty()) {
            int sp = name.indexOf(' ');
            g += ", " + (sp > 0 ? name.substring(0, sp) : name);
        }
        return g + "!";
    }

    int batteryPct() {
        try {
            return ((BatteryManager) getSystemService(BATTERY_SERVICE))
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Exception e) {
            return -1;
        }
    }

    void render(String title, List<Item> items, boolean isHome) {
        final float sc = prefs.getFloat("scale", 1f);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFFFF8E1);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), dp(24), dp(10), dp(24));
        sv.addView(col);

        TextView t = new TextView(this);
        t.setText(rainbow(title));
        t.setTextSize(42 * sc);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        col.addView(t);

        if (isHome) {
            t.setOnClickListener(v -> speakStatus());
            t.setOnLongClickListener(v -> { caregiverMenu(); return true; });

            TextView greet = new TextView(this);
            greet.setText(greeting());
            greet.setTextSize(24 * sc);
            greet.setTypeface(Typeface.DEFAULT_BOLD);
            greet.setTextColor(0xFF37474F);
            greet.setGravity(Gravity.CENTER);
            col.addView(greet);

            TextView sub = new TextView(this);
            sub.setText("(Tap my name to hear the time. Caregiver: long-press my name.)");
            sub.setTextSize(12);
            sub.setGravity(Gravity.CENTER);
            col.addView(sub);

            TextClock c = new TextClock(this);
            c.setFormat12Hour("h:mm a'\n'EEEE, d MMMM");
            c.setFormat24Hour("HH:mm'\n'EEEE, d MMMM");
            c.setTextSize(26 * sc);
            c.setGravity(Gravity.CENTER);
            c.setTextColor(0xFF37474F);
            c.setPadding(0, dp(8), 0, dp(2));
            col.addView(c);

            int pct = batteryPct();
            if (pct >= 0) {
                TextView bat = new TextView(this);
                bat.setGravity(Gravity.CENTER);
                bat.setTextSize(18 * sc);
                bat.setPadding(0, 0, 0, dp(8));
                if (pct < 20) {
                    bat.setText("🔋 Battery is low: " + pct + "% - plug in the charger");
                    bat.setTextColor(0xFFC62828);
                    bat.setTypeface(Typeface.DEFAULT_BOLD);
                } else {
                    bat.setText("🔋 Battery: " + pct + "%");
                    bat.setTextColor(0xFF2E7D32);
                }
                col.addView(bat);
            }
        } else {
            Item back = ic(tile("BACK", "", GREY, false, "back", () -> show("home")), "⬅️");
            col.addView(row(back, null));
        }

        Item pending = null;
        for (Item it : items) {
            if (it.header) {
                if (pending != null) { col.addView(row(pending, null)); pending = null; }
                TextView h = new TextView(this);
                h.setText(it.sw);
                h.setTextSize(19 * sc);
                h.setTypeface(Typeface.DEFAULT_BOLD);
                h.setTextColor(it.color);
                h.setPadding(dp(8), dp(18), 0, dp(4));
                col.addView(h);
            } else if (it.half) {
                if (pending == null) pending = it;
                else { col.addView(row(pending, it)); pending = null; }
            } else {
                if (pending != null) { col.addView(row(pending, null)); pending = null; }
                col.addView(row(it, null));
            }
        }
        if (pending != null) col.addView(row(pending, null));
        setContentView(sv);
    }

    View row(Item a, Item b) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.addView(makeTile(a));
        if (b != null) r.addView(makeTile(b));
        else if (a.half) {
            View spacer = new View(this);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(0, dp(10), 1f));
            r.addView(spacer);
        }
        return r;
    }

    View makeTile(final Item it) {
        StringBuilder sb = new StringBuilder();
        int iconEnd = 0;
        if (!it.icon.isEmpty()) {
            sb.append(it.icon);
            iconEnd = sb.length();
            sb.append(it.half ? "\n" : "  ");
        }
        sb.append(it.sw);
        int enStart = -1;
        if (!it.en.isEmpty()) { sb.append("\n"); enStart = sb.length(); sb.append(it.en); }
        SpannableString s = new SpannableString(sb.toString());
        if (iconEnd > 0) {
            s.setSpan(new RelativeSizeSpan(it.half ? 1.9f : 1.5f), 0, iconEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (enStart >= 0) {
            s.setSpan(new RelativeSizeSpan(0.55f), enStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        final float sc = prefs.getFloat("scale", 1f);
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize((it.half ? 21 : 24) * sc);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(it.color == YELLOW ? 0xFF212121 : 0xFFFFFFFF);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(6), dp(6), dp(6), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(it.color);
        bg.setCornerRadius(dp(18));
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(Math.round((it.half ? 125 : 90) * sc)), 1f);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> onTileTap(v, it));
        return tv;
    }

    void onTileTap(View v, Item it) {
        long now = SystemClock.uptimeMillis();
        if (now - lastTap < 700) return;          // ignore shaky double taps
        lastTap = now;
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (prefs.getBoolean("speakNames", true) && !it.sw.isEmpty()) speak(it.sw);
        it.run.run();
    }

    @Override
    public void onBackPressed() {
        if (!currentPage.equals("home")) show("home");
    }

    // ================= helpers =================

    void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    void speak(String s) {
        if (ttsReady) tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "nyalee");
    }

    void open(Intent i) {
        try { startActivity(i); }
        catch (Exception e) { toast("Not available on this phone"); }
    }

    Intent selector(String category) {
        return Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category);
    }

    boolean tryApp(String... pkgs) {
        for (String p : pkgs) {
            Intent i = getPackageManager().getLaunchIntentForPackage(p);
            if (i != null) { startActivity(i); return true; }
        }
        return false;
    }

    void app(String... pkgs) {
        if (!tryApp(pkgs)) toast("This app is not installed on the phone");
    }

    void mpesa() {
        if (!tryApp("com.android.stk", "com.android.stk2")) {
            open(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode("*334#"))));
        }
    }

    void recentCalls() {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setType(CallLog.Calls.CONTENT_TYPE);
        open(i);
    }

    void medicineReminder() {
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
        i.putExtra(AlarmClock.EXTRA_MESSAGE, "Take medicine");
        open(i);
    }

    void volume(int dir) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI);
    }

    void toggleTorch() {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            if (torchId == null) {
                for (String id : cm.getCameraIdList()) {
                    Boolean f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (f != null && f) { torchId = id; break; }
                }
            }
            if (torchId == null) { toast("Torch not available"); return; }
            torchOn = !torchOn;
            cm.setTorchMode(torchId, torchOn);
        } catch (Exception e) {
            toast("Torch not available");
        }
    }

    void callNumber(String num, String name) {
        boolean ok = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
        speak("Calling " + name);
        open(new Intent(ok ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num))));
    }

    String textSizeName(float sc) {
        return sc < 1.1f ? "Normal" : sc < 1.4f ? "Large" : "Extra large";
    }

    void cycleTextSize() {
        float cur = prefs.getFloat("scale", 1f);
        float next = cur < 1.1f ? 1.25f : cur < 1.4f ? 1.5f : 1f;
        prefs.edit().putFloat("scale", next).apply();
        toast("Text size: " + textSizeName(next));
        show(currentPage);
        speak("Text size: " + textSizeName(next));
    }

    // ================= talking status =================

    void speakStatus() {
        Date now = new Date();
        String time = new SimpleDateFormat("h:mm a", Locale.ENGLISH).format(now);
        String date = new SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH).format(now);
        int pct = batteryPct();
        speak(greeting() + " It is " + time + ", " + date + "."
                + (pct >= 0 ? " Your battery is at " + pct + " percent." : ""));
    }

    void speakBattery() {
        int pct = batteryPct();
        speak(pct < 0 ? "I cannot read the battery." : "Your battery is at " + pct + " percent."
                + (pct < 20 ? " Please plug in the charger." : ""));
    }

    // ================= voice =================

    void showGuide(final String title, final String text) {
        speak(text);
        final float sc = prefs.getFloat("scale", 1f);
        TextView body = new TextView(this);
        body.setText(text);
        body.setTextSize(22 * sc);
        body.setPadding(dp(20), dp(12), dp(20), dp(4));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(body)
                .setPositiveButton("OK", (d, w) -> { if (ttsReady) tts.stop(); })
                .setNeutralButton("Listen again", (d, w) -> showGuide(title, text))
                .show();
    }

    void listen() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);   // use the downloaded offline pack when present
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what you want...");
        try {
            startActivityForResult(i, 77);
        } catch (Exception e) {
            speak("Voice is not available. Please use the buttons.");
            toast("Voice is not available. Use the buttons, or press Voice Help.");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 77 && res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null) handleVoice(r);
        }
    }

    void handleVoice(List<String> heard) {
        List<Item> all = new ArrayList<>(home);
        all.addAll(tools);
        all.addAll(settings);
        all.addAll(help);
        all.addAll(nums);
        all.addAll(voiceOnly);
        Item best = null;
        int bestLen = 0;
        for (String h : heard) {
            String s = h.toLowerCase(Locale.ROOT);
            for (Item it : all) {
                if (it.header) continue;
                for (String k : it.keys.split(",")) {
                    k = k.trim();
                    if (!k.isEmpty() && s.contains(k) && k.length() > bestLen) { best = it; bestLen = k.length(); }
                }
            }
        }
        if (best != null) best.run.run();
        else {
            speak("Sorry, I did not understand. Please try again, or press Voice Help.");
            toast("Did not understand. Please try again.");
        }
    }

    // ================= caregiver menu (no PIN) =================

    void opt(List<String> labels, List<Runnable> acts, String label, Runnable r) {
        labels.add(label);
        acts.add(r);
    }

    void caregiverMenu() {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        opt(labels, acts, "SOS number 1 (" + prefs.getString("sos1", "not set") + ")", () -> askNumber("sos1"));
        opt(labels, acts, "SOS number 2 (" + prefs.getString("sos2", "not set") + ")", () -> askNumber("sos2"));
        for (int n = 1; n <= 4; n++) {
            final int slot = n;
            String nm = prefs.getString("fam" + n + "n", "");
            opt(labels, acts, "Family " + n + ": " + (nm.isEmpty() ? "not set" : nm), () -> askFamily(slot));
        }
        opt(labels, acts, "Emergency info (name, blood group...)", this::askInfo);
        opt(labels, acts, "Speak button names: " + (prefs.getBoolean("speakNames", true) ? "ON" : "OFF"), () -> {
            prefs.edit().putBoolean("speakNames", !prefs.getBoolean("speakNames", true)).apply();
            toast("Speak button names: " + (prefs.getBoolean("speakNames", true) ? "ON" : "OFF"));
        });
        opt(labels, acts, "Text size: " + textSizeName(prefs.getFloat("scale", 1f)), this::cycleTextSize);
        opt(labels, acts, "Set this app as the home screen", () -> open(new Intent(Settings.ACTION_HOME_SETTINGS)));
        opt(labels, acts, "Install apps (Play Store)", () -> app("com.android.vending"));
        opt(labels, acts, "All phone settings", () -> open(new Intent(Settings.ACTION_SETTINGS)));
        new AlertDialog.Builder(this).setTitle("Caregiver")
                .setItems(labels.toArray(new String[0]), (d, w) -> acts.get(w).run()).show();
    }

    void askNumber(final String key) {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_PHONE);
        et.setText(prefs.getString(key, ""));
        new AlertDialog.Builder(this).setTitle("Phone number (e.g. 0712345678)").setView(et)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.edit().putString(key, et.getText().toString().trim()).apply();
                    toast("Saved");
                }).setNegativeButton("Cancel", null).show();
    }

    void askFamily(final int slot) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText name = new EditText(this);
        name.setHint("Name (e.g. Mum, Dad, John)");
        name.setText(prefs.getString("fam" + slot + "n", ""));
        final EditText num = new EditText(this);
        num.setHint("Number (e.g. 0712345678)");
        num.setInputType(InputType.TYPE_CLASS_PHONE);
        num.setText(prefs.getString("fam" + slot + "p", ""));
        box.addView(name);
        box.addView(num);
        new AlertDialog.Builder(this).setTitle("Family " + slot + " (leave empty to delete)").setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.edit().putString("fam" + slot + "n", name.getText().toString().trim())
                            .putString("fam" + slot + "p", num.getText().toString().trim()).apply();
                    toast("Saved");
                    show("home");
                }).setNegativeButton("Cancel", null).show();
    }

    static final String[] INFO_KEYS = {"info_name", "info_blood", "info_cond", "info_meds", "info_addr"};
    static final String[] INFO_HINTS = {"Full name", "Blood group (e.g. O+)", "Medical conditions & allergies",
            "Medicines taken", "Home address / landmark"};
    static final String[] INFO_LABELS = {"Name", "Blood group", "Conditions & allergies", "Medicines", "Home"};

    void askInfo() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        sv.addView(box);
        final EditText[] ets = new EditText[INFO_KEYS.length];
        for (int i = 0; i < INFO_KEYS.length; i++) {
            ets[i] = new EditText(this);
            ets[i].setHint(INFO_HINTS[i]);
            ets[i].setText(prefs.getString(INFO_KEYS[i], ""));
            box.addView(ets[i]);
        }
        new AlertDialog.Builder(this).setTitle("Emergency info").setView(sv)
                .setPositiveButton("Save", (d, w) -> {
                    SharedPreferences.Editor e = prefs.edit();
                    for (int i = 0; i < INFO_KEYS.length; i++) e.putString(INFO_KEYS[i], ets[i].getText().toString().trim());
                    e.apply();
                    toast("Saved");
                    show("home");
                }).setNegativeButton("Cancel", null).show();
    }

    void showInfo() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < INFO_KEYS.length; i++) {
            String v = prefs.getString(INFO_KEYS[i], "");
            if (!v.isEmpty()) sb.append(INFO_LABELS[i]).append(": ").append(v).append("\n\n");
        }
        if (sb.length() == 0) sb.append("No information saved yet.\nAsk your caregiver to add it.");
        final float sc = prefs.getFloat("scale", 1f);
        TextView body = new TextView(this);
        body.setText(sb.toString().trim());
        body.setTextSize(24 * sc);
        body.setPadding(dp(20), dp(12), dp(20), dp(4));
        new AlertDialog.Builder(this).setTitle("My Info").setView(body).setPositiveButton("OK", null).show();
    }

    // ================= SOS and "I'm OK" =================

    boolean haveSosPerms() {
        return checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    void askPerms(int action) {
        pendingAction = action;
        requestPermissions(new String[]{Manifest.permission.SEND_SMS, Manifest.permission.CALL_PHONE,
                Manifest.permission.ACCESS_FINE_LOCATION}, 5);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == 5) {                 // uses whatever was allowed; SOS falls back to the dialer
            if (pendingAction == 1) doOk(); else doSos();
        }
    }

    boolean noSosNumber() {
        if (!prefs.getString("sos1", "").isEmpty()) return false;
        new AlertDialog.Builder(this)
                .setMessage("The SOS number is not set.\nAsk your caregiver to set it.\n(Caregiver: long-press the name Nyalee)")
                .setPositiveButton("OK", null).show();
        return true;
    }

    String locationLink() {
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return "";
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best != null) {
                return " Location: https://maps.google.com/?q=" + best.getLatitude() + "," + best.getLongitude();
            }
        } catch (Exception ignored) { }
        return "";
    }

    boolean smsToFamily(String msg) {
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false;
        boolean sent = false;
        for (String key : new String[]{"sos1", "sos2"}) {
            String num = prefs.getString(key, "");
            if (!num.isEmpty()) {
                try {
                    SmsManager sm = Build.VERSION.SDK_INT >= 31
                            ? getSystemService(SmsManager.class) : SmsManager.getDefault();
                    sm.sendMultipartTextMessage(num, null, sm.divideMessage(msg), null, null);
                    sent = true;
                } catch (Exception ignored) { }
            }
        }
        return sent;
    }

    String who() {
        String n = prefs.getString("info_name", "").trim();
        return n.isEmpty() ? "" : " from " + n;
    }

    void sosConfirm() {
        if (noSosNumber()) return;
        speak("Do you want to ask for help?");
        new AlertDialog.Builder(this)
                .setTitle("ASK FOR HELP?")
                .setMessage("Your family will be called and sent a message with your location.")
                .setPositiveButton("YES - ASK FOR HELP", (d, w) -> { if (haveSosPerms()) doSos(); else askPerms(0); })
                .setNegativeButton("NO", null)
                .show();
    }

    void doSos() {
        smsToFamily("SOS" + who() + "! I need help urgently. Please call me." + locationLink());
        boolean callOk = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
        open(new Intent(callOk ? Intent.ACTION_CALL : Intent.ACTION_DIAL,
                Uri.parse("tel:" + Uri.encode(prefs.getString("sos1", "")))));
        speak("Calling for help.");
    }

    void okConfirm() {
        if (noSosNumber()) return;
        speak("Do you want to tell your family you are okay?");
        new AlertDialog.Builder(this)
                .setTitle("TELL MY FAMILY I'M OK?")
                .setMessage("A message will be sent to your family with your location.")
                .setPositiveButton("YES - SEND", (d, w) -> { if (haveSosPerms()) doOk(); else askPerms(1); })
                .setNegativeButton("NO", null)
                .show();
    }

    void doOk() {
        boolean sent = smsToFamily("Hello" + who() + ". I am fine, do not worry." + locationLink());
        String m = sent ? "Your family has been told you are fine." : "The message could not be sent.";
        toast(m);
        speak(m);
    }
}
