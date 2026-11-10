package de.jiy.family;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Bildschirm „Diese App ist nicht freigegeben“. */
public class BlockActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String pkg = getIntent().getStringExtra("pkg");
        String name = pkg;
        try {
            PackageManager pm = getPackageManager();
            name = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception ignored) {
        }
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding((int) (28 * d), (int) (28 * d), (int) (28 * d), (int) (28 * d));
        box.setBackgroundColor(Color.parseColor("#FFF8F0"));

        TextView icon = new TextView(this);
        icon.setText("time".equals(getIntent().getStringExtra("reason")) ? "⏳" : "🔒");
        icon.setTextSize(64);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon);

        TextView t = new TextView(this);
        boolean time = "time".equals(getIntent().getStringExtra("reason"));
        int lim = getIntent().getIntExtra("limit", 0);
        t.setText(time ? name + "\nZeit für heute ist um" : name + "\nist nicht freigegeben");
        t.setTextSize(22);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#2B2118"));
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, (int) (12 * d), 0, (int) (8 * d));
        box.addView(t);

        TextView s = new TextView(this);
        s.setText(time ? "Heute erlaubt: " + lim + " Minuten. Morgen geht es wieder!" : "Frag Mama oder Papa, ob sie die App in JIY freigeben.");
        s.setTextSize(16);
        s.setTextColor(Color.parseColor("#6B5E52"));
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, 0, 0, (int) (24 * d));
        box.addView(s);

        Button home = new Button(this);
        home.setText("Zum Startbildschirm");
        home.setOnClickListener(v -> goHome());
        box.addView(home);

        Button jiy = new Button(this);
        jiy.setText("JIY öffnen");
        jiy.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        box.addView(jiy);

        Button sos = new Button(this);
        sos.setText("🆘 Notruf 112");
        sos.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        });
        box.addView(sos);

        setContentView(box);
    }

    private void goHome() {
        Intent h = new Intent(Intent.ACTION_MAIN);
        h.addCategory(Intent.CATEGORY_HOME);
        h.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(h);
        finish();
    }

    @Override
    public void onBackPressed() {
        goHome();
    }
}
