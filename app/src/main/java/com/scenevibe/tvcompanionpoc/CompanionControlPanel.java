package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.BooleanSupplier;

/**
 * Temporary remote-control window owned by the existing OverlayService.
 * Editorial SceneRenderer windows remain nonfocusable and untouched. Only this
 * visible window receives D-pad/Back; closing removes its window immediately,
 * without a transport command, a playback permit or a change to either toggle.
 * A system-owned notification PendingIntent invokes it; this is not a global
 * key listener and cannot establish which Sony remote opens notifications.
 */
final class CompanionControlPanel {
    /** Read the existing service's state and route every mutation back to that same owner. */
    interface Commands {
        /** Read a fresh immutable projection, never an independent UI preference store. */
        Snapshot snapshot();
        /** Change only the existing TEXT preference. */
        void text(boolean enabled);
        /** Change only the existing AUDIO preference. */
        void audio(boolean enabled);
        /** Explicitly suspend only SceneVibe effects. */
        void suspend();
        /** Request confirmation without claiming that MEDIA has resumed yet. */
        void resume();
        /** Apply a user-confirmed film position, reporting a specific refusal. */
        AdSyncController.Calibration calibrate(long time,boolean seek);
    }

    /** Future channels can extend this surface; no interlude engine or state is invented. */
    enum Channel { TEXT, AUDIO, INTERLUDES }

    /** These are requested switches and observed synchronization, not acoustic/display proof. */
    static final class Snapshot {
        final boolean active,text,audio;
        final String synchronization;
        /** Project only service-owned switches and bounded diagnostic text. */
        Snapshot(boolean active,boolean text,boolean audio,String synchronization) {
            this.active=active;this.text=text;this.audio=audio;this.synchronization=synchronization;
        }
    }

    /** Isolate only WindowManager effects so failures and removal can be qualified deterministically. */
    interface WindowHost {
        /** Attach one visible, focusable control window. */
        void add(View view,WindowManager.LayoutParams params);
        /** Release input before removal, even if removal itself fails. */
        void releaseFocus(View view,WindowManager.LayoutParams params);
        /** Remove the window synchronously; no detached root may retain input. */
        void remove(View view);
    }

    private final Context context;
    private final Commands commands;
    private final WindowHost host;
    private final BooleanSupplier permission;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=this::tick;
    private PanelRoot root;
    private LinearLayout page;
    private WindowManager.LayoutParams params;
    private Button text,audio,suspend,resume,recalibrate;
    private TextView status,message,time;
    private boolean calibration,closing;
    private String digits="";
    private CheckBox replay;
    private String feedback="Aucun signal publicitaire Prime garanti.";

    /** Use the already-granted overlay permission and the service's own WindowManager. */
    CompanionControlPanel(Context service,Commands commands) {
        this(service,commands,windows(service),()->Settings.canDrawOverlays(service));
    }

    /** Inject only window effects and permission checks; the command authority remains external. */
    CompanionControlPanel(Context service,Commands commands,WindowHost host,BooleanSupplier permission) {
        context=new ContextThemeWrapper(service,android.R.style.Theme_Material_Light_NoActionBar);
        this.commands=commands;this.host=host;this.permission=permission;
    }

    /** Implement the real native host without changing the editorial renderer's flags. */
    private static WindowHost windows(Context context) {
        WindowManager manager=context.getSystemService(WindowManager.class);
        return new WindowHost() {
            /** Attach through the public overlay window type. */
            @Override public void add(View view,WindowManager.LayoutParams params){manager.addView(view,params);}
            /** An invisible/closing window must become unable to retain keyboard or touch input. */
            @Override public void releaseFocus(View view,WindowManager.LayoutParams params) {
                params.flags|=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                view.clearFocus();view.setVisibility(View.GONE);
                try{manager.updateViewLayout(view,params);}catch(RuntimeException alreadyDetached){/* Removal still follows. */}
            }
            /** Remove synchronously rather than leaving focus to a later animation. */
            @Override public void remove(View view){manager.removeViewImmediate(view);}
        };
    }

    /** Show a single temporary window; any partial add failure is cleaned up and releases input. */
    boolean open() {
        if(root!=null)return true;
        if(!BuildConfig.TVUX_CONTROL_PANEL || !permission.getAsBoolean())return false;
        root=new PanelRoot(context);
        root.setBackground(background(Color.WHITE,Color.BLACK,2));
        root.setPadding(dp(12),dp(8),dp(12),dp(8));
        android.util.DisplayMetrics display=context.getResources().getDisplayMetrics();
        params=new WindowManager.LayoutParams(Math.min(dp(440),(int)(display.widthPixels*.48f)),
                (int)(display.heightPixels*.9f),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.END|Gravity.CENTER_VERTICAL;
        params.setTitle("SceneVibe companion controls");
        showMain();
        try{host.add(root,params);root.post(()->{if(root!=null)text.requestFocus();});handler.postDelayed(refresh,250);return true;}
        catch(RuntimeException refused){close();return false;}
    }

    /** Back and every close cross remove only this window, preserving service/session and switches. */
    void close() {
        if(root==null || closing)return;
        closing=true;handler.removeCallbacks(refresh);
        PanelRoot retiring=root;root=null;
        retiring.clearFocus();retiring.setVisibility(View.GONE);
        try{host.releaseFocus(retiring,params);}catch(RuntimeException refused){/* Removal remains mandatory. */}finally{
            try{host.remove(retiring);}catch(RuntimeException alreadyDetached){/* Focus was released first. */}
            closing=false;
        }
    }

    /** Report window ownership, independently of the commentary lifetime. */
    boolean isOpen(){return root!=null;}

    /** Pop the in-window form before closing the main panel; neither action stops comments. */
    void back(){if(calibration)showMain();else close();}

    /** Rebuild only on a route change, keeping live status refresh from moving D-pad focus. */
    private void shell(String title,Runnable dismiss) {
        root.removeAllViews();
        LinearLayout content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout heading=new LinearLayout(context);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView caption=label(title,24);heading.addView(caption,new LinearLayout.LayoutParams(0,dp(52),1));
        Button cross=button("× Fermer",dismiss);cross.setContentDescription("Fermer "+title);
        heading.addView(cross,new LinearLayout.LayoutParams(dp(132),dp(52)));content.addView(heading);
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(false);
        page=new LinearLayout(context);page.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(page);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(content,new FrameLayout.LayoutParams(-1,-1));
    }

    /** Render requested switches from the same owner used by the main application's Settings. */
    private void showMain() {
        calibration=false;shell("SceneVibe",this::close);
        status=label("",19);page.addView(status);
        text=action("Commentaires écrits",()->{commands.text(!commands.snapshot().text);update();});
        audio=action("Commentaires audio",()->{commands.audio(!commands.snapshot().audio);update();});
        suspend=action("Suspendre les commentaires",()->{commands.suspend();feedback="Suspension demandée.";update();});
        resume=action("Reprendre les commentaires",()->{commands.resume();feedback="Reprise demandée ; confirmation MEDIA attendue.";update();});
        recalibrate=action("Recaler sur le temps du film",this::showCalibration);
        message=label(feedback,17);page.addView(message);
        page.addView(label("Back ou × : fermer sans arrêter les commentaires.",17));
        update();
        text.requestFocus();
    }

    /** Provide an on-screen D-pad keypad; no IME, mouse or physical keyboard is required. */
    private void showCalibration() {
        calibration=true;digits="";shell("Temps du film",this::showMain);
        page.addView(label("Confirme que le film a repris. Saisis mm:ss ou hh:mm:ss. Correction locale : ±1 heure.",18));
        time=label("mm:ss ou hh:mm:ss",26);time.setContentDescription("Temps saisi");page.addView(time);
        String[] keys={"1","2","3","4","5","6","7","8","9",":","0","⌫"};
        Button[] cells=new Button[keys.length];
        for(int row=0;row<4;row++) {
            LinearLayout line=new LinearLayout(context);
            for(int col=0;col<3;col++) {
                final String key=keys[row*3+col];
                Button cell=button(key,()->editTime(key));cells[row*3+col]=cell;
                line.addView(cell,new LinearLayout.LayoutParams(0,dp(48),1));
            }
            page.addView(line);
        }
        for(int i=0;i<cells.length;i++){
            if(i%3>0)cells[i].setNextFocusLeftId(cells[i-1].getId());
            if(i%3<2)cells[i].setNextFocusRightId(cells[i+1].getId());
            if(i>=3)cells[i].setNextFocusUpId(cells[i-3].getId());
            if(i+3<cells.length)cells[i].setNextFocusDownId(cells[i+3].getId());
        }
        replay=new CheckBox(context);replay.setText("J’ai déplacé le film : autoriser la relecture");replay.setTextColor(Color.BLACK);
        replay.setTextSize(18);replay.setChecked(false);page.addView(replay);
        action("Confirmer le temps",()->{
            try{
                AdSyncController.Calibration result=commands.calibrate(AdSyncController.parseFilmTime(digits),replay.isChecked());
                feedback=result.message;
                if(result==AdSyncController.Calibration.APPLIED)showMain();else message.setText(feedback);
            }catch(IllegalArgumentException format){feedback=AdSyncController.Calibration.FORMAT_INVALID.message;message.setText(feedback);}
        });
        message=label("Le recalage ne modifie ni le film ni la track.",17);page.addView(message);
        cells[0].requestFocus();
    }

    /** Edit only a bounded local string; rejected input leaves the same form available for correction. */
    private void editTime(String key) {
        if("⌫".equals(key)){if(!digits.isEmpty())digits=digits.substring(0,digits.length()-1);}
        else if(digits.length()<9)digits+=key;
        time.setText(digits.isEmpty()?"mm:ss ou hh:mm:ss":digits);
    }

    /** Refresh state without claiming that a requested resume has already produced a MEDIA observation. */
    private void update() {
        if(root==null || calibration)return;
        Snapshot state=commands.snapshot();
        setTextIfChanged(text,"Commentaires écrits : "+(state.text?"ON":"OFF"));text.setEnabled(state.active);
        setTextIfChanged(audio,"Commentaires audio : "+(state.audio?"ON":"OFF"));audio.setEnabled(state.active);
        suspend.setEnabled(state.active);resume.setEnabled(state.active);recalibrate.setEnabled(state.active);
        setTextIfChanged(status,(state.active?"Session Companion active":"Session Companion inactive")+"\n"+state.synchronization);
        setTextIfChanged(message,feedback);
    }

    /** Avoid repeated layout/accessibility events while the service state has not changed. */
    private void setTextIfChanged(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}

    /** Close on permission loss; refreshing a view never starts/restarts the commentary service. */
    private void tick() {
        if(root==null)return;
        if(!permission.getAsBoolean()){close();return;}
        update();handler.postDelayed(refresh,250);
    }

    /** Add one full-width action with a stable focus ID. */
    private Button action(String title,Runnable work) {
        Button result=button(title,work);page.addView(result,new LinearLayout.LayoutParams(-1,dp(52)));return result;
    }

    /** Make focus visible through outline, inversion and contrast, rather than a color-only state. */
    private Button button(String title,Runnable work) {
        Button result=new Button(context);result.setId(View.generateViewId());result.setText(title);result.setTextSize(18);
        result.setAllCaps(false);result.setFocusable(true);result.setMinHeight(0);result.setMinimumHeight(0);
        result.setOnClickListener(view->work.run());
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},background(Color.BLACK,Color.WHITE,4));
        states.addState(new int[]{android.R.attr.state_pressed},background(Color.BLACK,Color.WHITE,4));
        states.addState(new int[]{},background(0xFFF1F1F1,0xFF555555,1));result.setBackground(states);
        result.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused},{android.R.attr.state_pressed},{}},
                new int[]{Color.WHITE,Color.WHITE,Color.BLACK}));
        return result;
    }

    /** Draw an opaque, high-contrast shape so underlying video never determines label readability. */
    private GradientDrawable background(int fill,int stroke,int width) {
        GradientDrawable shape=new GradientDrawable();shape.setColor(fill);shape.setStroke(dp(width),stroke);shape.setCornerRadius(dp(6));return shape;
    }

    /** Use explicit TV-readable sizes and multiline labels. */
    private TextView label(String text,int size) {
        TextView view=new TextView(context);view.setText(text);view.setTextColor(Color.BLACK);view.setTextSize(size);
        view.setPadding(dp(4),dp(4),dp(4),dp(4));return view;
    }

    /** Scale dimensions with Android's density rather than assuming a Sony pixel resolution. */
    private int dp(int size){return Math.round(size*context.getResources().getDisplayMetrics().density);}

    /** Consume Back only while this visible, explicitly opened control window owns input. */
    private final class PanelRoot extends FrameLayout {
        private boolean hadFocus;
        /** This root is separate from every passive editorial window. */
        PanelRoot(Context context){super(context);}
        /** Consume both halves of Back so its release cannot navigate the application underneath. */
        @Override public boolean dispatchKeyEvent(KeyEvent event) {
            if(event.getKeyCode()==KeyEvent.KEYCODE_BACK) {
                if(event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled())back();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        /** Unexpected system-window focus loss closes the panel rather than keeping an invisible input owner. */
        @Override public void onWindowFocusChanged(boolean focused) {
            super.onWindowFocusChanged(focused);
            if(focused)hadFocus=true;else if(hadFocus && root==this)close();
        }
        /** Remove polling and ownership even if Android detaches the window unexpectedly. */
        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if(root==this)close();
        }
    }
}
