package com.example.breathguide;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.SharedPreferences;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MainActivity extends Activity {
    private enum Phase { HOME, INHALE, EXHALE, RETENTION, RECOVERY, RELEASE, DONE, HISTORY }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int ink = Color.rgb(27, 49, 65), teal = Color.rgb(20, 116, 117);
    private Phase phase = Phase.HOME;
    private LinearLayout page;
    private TextView headline, detail, clock;
    private Button primary;
    private int breaths = 30, rounds = 3, inhaleSeconds = 2, exhaleSeconds = 2;
    private int breath = 1, round = 1;
    private long phaseStart, retentionStart, sessionStart;
    private JSONArray results = new JSONArray();
    private boolean sessionSaved = false;
    private TextToSpeech speech;
    private boolean speechReady = false, voiceEnabled = true;
    private final Runnable ticker = new Runnable() { public void run() { advance(); handler.postDelayed(this, 100); } };

    @Override @SuppressWarnings("deprecation")
    public void onCreate(Bundle saved) {
        super.onCreate(saved); getWindow().setStatusBarColor(ink); getWindow().setNavigationBarColor(ink);
        voiceEnabled=getPreferences(MODE_PRIVATE).getBoolean("voice",true);
        speech=new TextToSpeech(this,status->{
            if(status==TextToSpeech.SUCCESS && speech!=null) {
                int result=speech.setLanguage(Locale.ENGLISH);
                speechReady=result!=TextToSpeech.LANG_MISSING_DATA && result!=TextToSpeech.LANG_NOT_SUPPORTED;
                speech.setSpeechRate(1.15f);
                if(active()) speakCue();
            }
        });
        showHome();
    }
    @Override protected void onPause() { super.onPause(); if (active()) stopSession(); }
    @Override protected void onDestroy() { handler.removeCallbacks(ticker); if(speech!=null) { speech.stop(); speech.shutdown(); } super.onDestroy(); }
    private boolean active() { return phase == Phase.INHALE || phase == Phase.EXHALE || phase == Phase.RETENTION || phase == Phase.RECOVERY || phase == Phase.RELEASE; }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String s, int size) { TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(ink); v.setGravity(Gravity.CENTER); v.setPadding(dp(8),dp(10),dp(8),dp(10)); return v; }
    private void base(String title) {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(244,248,248));
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setGravity(Gravity.CENTER_HORIZONTAL); page.setPadding(dp(20),dp(22),dp(20),dp(24));
        scroll.addView(page); setContentView(scroll);
        TextView h=label(title,28); h.setTypeface(null,Typeface.BOLD); page.addView(h);
    }
    private Button button(String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setTextSize(18); b.setTextColor(Color.WHITE);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(teal); bg.setCornerRadius(dp(16)); b.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(62)); p.setMargins(0,dp(10),0,dp(8)); page.addView(b,p); b.setOnClickListener(v->action.run()); return b;
    }
    private void spacer(int h) { View v=new View(this); page.addView(v,new LinearLayout.LayoutParams(1,dp(h))); }
    private int setting(String key,int fallback) { return getPreferences(MODE_PRIVATE).getInt(key,fallback); }
    private void showHome() {
        phase=Phase.HOME; getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        base(getString(R.string.app_name));
        page.addView(label(getString(R.string.subtitle_home),16));
        breaths=setting("breaths",30); rounds=setting("rounds",3); inhaleSeconds=setting("inhale",2); exhaleSeconds=setting("exhale",2);
        NumberPicker breathPicker=picker(getString(R.string.picker_breaths),10,60,breaths);
        NumberPicker roundPicker=picker(getString(R.string.picker_rounds),1,10,rounds);
        NumberPicker inPicker=picker(getString(R.string.picker_inhale),1,8,inhaleSeconds);
        NumberPicker outPicker=picker(getString(R.string.picker_exhale),1,8,exhaleSeconds);
        Switch voiceSwitch=new Switch(this); voiceSwitch.setText(getString(R.string.switch_voice)); voiceSwitch.setTextSize(17);
        voiceSwitch.setChecked(voiceEnabled); page.addView(voiceSwitch);
        spacer(12);
        page.addView(label(getString(R.string.disclaimer),15));
        button(getString(R.string.btn_start_session),()->{
            breaths=breathPicker.getValue(); rounds=roundPicker.getValue(); inhaleSeconds=inPicker.getValue(); exhaleSeconds=outPicker.getValue();
            voiceEnabled=voiceSwitch.isChecked();
            getPreferences(MODE_PRIVATE).edit().putInt("breaths",breaths).putInt("rounds",rounds).putInt("inhale",inhaleSeconds).putInt("exhale",exhaleSeconds).putBoolean("voice",voiceEnabled).apply(); startSession();
        });
        button(getString(R.string.btn_daily_history),this::showHistory);
    }
    private NumberPicker picker(String caption,int min,int max,int value) {
        page.addView(label(caption,17)); NumberPicker p=new NumberPicker(this); p.setMinValue(min); p.setMaxValue(max); p.setValue(value); p.setWrapSelectorWheel(false); page.addView(p); return p;
    }
    private void startSession() {
        results=new JSONArray(); sessionSaved=false; round=1; breath=1; sessionStart=System.currentTimeMillis();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        base(getString(R.string.title_round_header, 1, rounds));
        headline=label(getString(R.string.title_inhale),34); headline.setTypeface(null,Typeface.BOLD); page.addView(headline);
        detail=label(getString(R.string.sub_breath_count, 1, breaths),19); page.addView(detail);
        clock=label("",68); clock.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL)); page.addView(clock);
        spacer(28);
        primary=button(getString(R.string.btn_next_inhale),this::next);
        button(getString(R.string.btn_stop_session),this::stopSession);
        enter(Phase.INHALE); handler.removeCallbacks(ticker); handler.post(ticker);
    }
    private void enter(Phase p) { phase=p; phaseStart=SystemClock.elapsedRealtime(); render(); speakCue(); }
    private void speakCue() {
        if(!voiceEnabled || !speechReady || speech==null || !active()) return;
        String cue;
        switch(phase) {
            case INHALE: cue=getString(R.string.cue_breathe_in); break;
            case EXHALE:
            case RELEASE: cue=getString(R.string.cue_breathe_out); break;
            case RETENTION: cue=getString(R.string.cue_retention); break;
            case RECOVERY: cue=getString(R.string.cue_recovery); break;
            default: return;
        }
        speech.speak(cue,TextToSpeech.QUEUE_FLUSH,null,phase.name()+"-"+round+"-"+breath);
    }
    private void advance() {
        if (!active()) return;
        long elapsed=SystemClock.elapsedRealtime()-phaseStart;
        if (phase==Phase.INHALE && elapsed>=inhaleSeconds*1000L) enter(Phase.EXHALE);
        else if (phase==Phase.EXHALE && elapsed>=exhaleSeconds*1000L) {
            if (breath==breaths) { enter(Phase.RETENTION); retentionStart=phaseStart; }
            else { breath++; enter(Phase.INHALE); }
        } else if (phase==Phase.RECOVERY && elapsed>=10000) enter(Phase.RELEASE);
        else if (phase==Phase.RELEASE && elapsed>=3000) {
            if (round<rounds) { round++; breath=1; enter(Phase.INHALE); }
            else finish(false);
        }
        render();
    }
    private void render() {
        if (!active() || headline==null) return;
        long elapsed=SystemClock.elapsedRealtime()-phaseStart;
        String title, sub;
        switch(phase) {
            case INHALE: title=getString(R.string.title_inhale_deeply); sub=getString(R.string.sub_breath_count, breath, breaths); break;
            case EXHALE: title=getString(R.string.title_exhale_gently); sub=getString(R.string.sub_breath_count, breath, breaths); break;
            case RETENTION: title=getString(R.string.title_hold_after_exhale); sub=getString(R.string.sub_tap_next); break;
            case RECOVERY: title=getString(R.string.title_deep_inhale_hold); sub=getString(R.string.sub_recovery_breath); break;
            case RELEASE: title=getString(R.string.title_exhale_rest); sub=round<rounds ? getString(R.string.sub_next_round_soon) : getString(R.string.sub_session_ending); break;
            default: return;
        }
        headline.setText(title); detail.setText(getString(R.string.detail_round_status, round, rounds, sub));
        if (phase==Phase.RETENTION) clock.setText(format(SystemClock.elapsedRealtime()-retentionStart));
        else { int duration=phase==Phase.INHALE?inhaleSeconds:phase==Phase.EXHALE?exhaleSeconds:phase==Phase.RECOVERY?10:3; clock.setText(String.valueOf(Math.max(0,(duration*1000L-elapsed+999)/1000))); }
        primary.setVisibility(phase==Phase.RETENTION?View.VISIBLE:View.GONE);
    }
    private void next() {
        if (phase!=Phase.RETENTION) return;
        long duration=SystemClock.elapsedRealtime()-retentionStart;
        JSONObject item=new JSONObject(); try { item.put("round",round); item.put("retentionMs",duration); results.put(item); } catch(Exception ignored) {}
        enter(Phase.RECOVERY);
    }
    private void stopSession() { if (!active()) return; finish(true); }
    private void finish(boolean stopped) {
        handler.removeCallbacks(ticker); getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(speech!=null) speech.stop();
        if (!sessionSaved) {
            try {
                JSONObject session=new JSONObject(); session.put("startedAt",sessionStart); session.put("breaths",breaths); session.put("plannedRounds",rounds);
                session.put("inhaleSeconds",inhaleSeconds); session.put("exhaleSeconds",exhaleSeconds); session.put("stopped",stopped); session.put("results",results);
                SharedPreferences prefs=getPreferences(MODE_PRIVATE); JSONArray history=new JSONArray(prefs.getString("sessions","[]")); history.put(session);
                prefs.edit().putString("sessions",history.toString()).apply(); sessionSaved=true;
            } catch(Exception e) { Toast.makeText(this,getString(R.string.toast_could_not_save),Toast.LENGTH_LONG).show(); }
        }
        phase=Phase.DONE; base(stopped?getString(R.string.title_session_stopped):getString(R.string.title_session_complete));
        page.addView(label(getString(R.string.label_completed_retention),20));
        if (results.length()==0) page.addView(label(getString(R.string.label_no_completed_rounds),17));
        for(int i=0;i<results.length();i++) { JSONObject item=results.optJSONObject(i); page.addView(label(getString(R.string.round_result_format, item.optInt("round"), format(item.optLong("retentionMs"))),24)); }
        button(getString(R.string.btn_view_daily_history),this::showHistory); button(getString(R.string.btn_new_session),this::showHome);
    }
    private String format(long ms) { long seconds=Math.max(0,ms)/1000; return String.format(Locale.getDefault(),"%d:%02d",seconds/60,seconds%60); }
    private void showHistory() {
        phase=Phase.HISTORY; base(getString(R.string.btn_daily_history));
        JSONArray history; try { history=new JSONArray(getPreferences(MODE_PRIVATE).getString("sessions","[]")); } catch(Exception e) { history=new JSONArray(); }
        DateTimeFormatter day=DateTimeFormatter.ofPattern("EEE, MMM d, yyyy · h:mm a",Locale.getDefault());
        if(history.length()==0) page.addView(label(getString(R.string.label_no_sessions_saved),18));
        for(int i=history.length()-1;i>=0;i--) {
            JSONObject s=history.optJSONObject(i); if(s==null) continue;
            String date=Instant.ofEpochMilli(s.optLong("startedAt")).atZone(ZoneId.systemDefault()).format(day);
            TextView heading=label(date + (s.optBoolean("stopped")?" · stopped":""),18); heading.setTypeface(null,Typeface.BOLD); page.addView(heading);
            JSONArray a=s.optJSONArray("results");
            if(a==null || a.length()==0) page.addView(label(getString(R.string.label_no_completed_rounds),15));
            else for(int j=0;j<a.length();j++) { JSONObject x=a.optJSONObject(j); page.addView(label(getString(R.string.round_history_format, x.optInt("round"), format(x.optLong("retentionMs"))),19)); }
        }
        button(getString(R.string.btn_back_to_setup),this::showHome);
    }
    @Override @SuppressWarnings("deprecation")
    public void onBackPressed() { if(active()) stopSession(); else showHome(); }
}
