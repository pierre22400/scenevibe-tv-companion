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
import android.util.Log;
import android.util.SparseBooleanArray;
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
 * TV-UX 002-A keeps automatic visibility passive. An explicit focus experiment
 * is separate; public Android cannot give this D-pad window input focus while
 * promising that the covered application retains the same window focus.
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
        /** Older recording ports expose no AD-SYNC toggle; unsupported commands fail closed. */
        default void adSync(boolean enabled){throw new UnsupportedOperationException("ADSYNC_CONTROL_UNAVAILABLE");}
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
        final String synchronization,timing;
        final boolean adSync,adSyncSupported;
        /** Project only service-owned switches and bounded diagnostic text. */
        Snapshot(boolean active,boolean text,boolean audio,String synchronization) {
            this(active,text,audio,false,false,synchronization,"");
        }
        /** Read switches, current synchronization and sanitized temporal diagnostics from the one owner. */
        Snapshot(boolean active,boolean text,boolean audio,boolean adSync,boolean supported,String synchronization,String timing) {
            this.active=active;this.text=text;this.audio=audio;this.adSync=adSync;adSyncSupported=supported;
            this.synchronization=synchronization;this.timing=timing;
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
        /** Change only this window's size/input policy without replacing its root. */
        default void update(View view,WindowManager.LayoutParams params){throw new UnsupportedOperationException("WINDOW_UPDATE_UNAVAILABLE");}
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
    private Button text,audio,adToggle,suspend,resume,recalibrate;
    private TextView status,message,time,timing;
    private boolean embedded,embeddedPaused,passive,monitoring;
    private Runnable closed=()->{},parked=()->{},embeddedDismiss=()->{};
    private long lastTimingUpdate=-1;
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
            /** Prepared and interactive are two input policies of the very same window. */
            @Override public void update(View view,WindowManager.LayoutParams params){manager.updateViewLayout(view,params);}
        };
    }

    /** Show a single temporary window; any partial add failure is cleaned up and releases input. */
    boolean open() {
        if(root!=null)return passive?activatePrepared():true;
        return attach(false);
    }

    /** Keep the prepared window visible but unable to intercept any key or touch. */
    boolean prepare() {
        if(!BuildConfig.TVUX_DEVELOPER_OVERLAY)return false;
        if(root!=null){standby();return true;}
        return attach(true);
    }

    /** Attach once, using an explicit input policy rather than a delayed global shortcut. */
    private boolean attach(boolean prepared) {
        if(!BuildConfig.TVUX_CONTROL_PANEL || !permission.getAsBoolean())return false;
        passive=prepared;monitoring=false;root=new PanelRoot(context);
        root.setBackground(background(Color.WHITE,Color.BLACK,2));root.setPadding(dp(12),dp(8),dp(12),dp(8));
        params=new WindowManager.LayoutParams(-2,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.END|Gravity.CENTER_VERTICAL;params.setTitle("SceneVibe companion controls");
        configureSize(prepared);
        if(prepared)showPrepared();else showMain();
        try{host.add(root,params);trace("ATTACHED",-1,-1);if(!prepared)root.post(()->{if(root!=null && !passive)text.requestFocus();});handler.postDelayed(refresh,250);return true;}
        catch(RuntimeException refused){close();return false;}
    }

    /** Promote only the already-prepared window after the source Activity has stopped. */
    boolean activatePrepared() {
        return presentPrepared(true);
    }

    /** Expand the same window; normal presentation keeps both no-input flags and never requests focus. */
    boolean presentPrepared(boolean interactive) {
        if(root==null || !passive || !permission.getAsBoolean())return false;
        passive=!interactive;monitoring=!interactive;root.hadFocus=false;root.held.clear();configureSize(false);showMain();
        try{host.update(root,params);trace(interactive?"EXPLICIT_FOCUS_REQUEST":"PASSIVE_EXPANDED",-1,-1);
            if(interactive)root.post(()->{if(root!=null && !passive)text.requestFocus();});return true;}
        catch(RuntimeException refused){close();return false;}
    }

    /** Release focus while retaining the same window and every business switch. */
    void standby() {
        if(root==null || embedded || (passive && !monitoring))return;
        passive=true;monitoring=false;root.held.clear();configureSize(true);root.clearFocus();showPrepared();
        try{host.update(root,params);trace("PARKED",-1,-1);parked.run();}catch(RuntimeException refused){close();}
    }

    /** Size and focus are independent from the passive editorial overlays. */
    private void configureSize(boolean prepared) {
        var display=context.getResources().getDisplayMetrics();
        params.width=prepared?Math.min(dp(320),(int)(display.widthPixels*.4f)):Math.min(dp(440),(int)(display.widthPixels*.48f));
        params.height=prepared?dp(76):(int)(display.heightPixels*.9f);
        root.setDescendantFocusability(passive?android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS:android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if(passive)params.flags|=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else params.flags&=~(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    /** Explain the prepared state without adding a focusable handle or a fake global key. */
    private void showPrepared() {
        root.removeAllViews();root.addView(label("Développeur · touches libres\nLance le film dans le lecteur.",17));
    }

    /** The Activity embeds these same widgets as ordinary Views; no WindowManager add occurs here. */
    View embeddedView(Runnable dismiss) {
        embedded=true;embeddedDismiss=dismiss;root=new PanelRoot(context);
        root.setBackground(background(Color.WHITE,Color.BLACK,2));root.setPadding(dp(12),dp(8),dp(12),dp(8));
        root.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(630)));showMain();handler.postDelayed(refresh,250);return root;
    }

    /** Pause only presentation polling when the normal Activity leaves the foreground. */
    void pauseEmbedded(){if(embedded){embeddedPaused=true;handler.removeCallbacks(refresh);}}
    /** Refresh the ordinary Activity widgets on return, without changing the service window. */
    void resumeEmbedded(){if(embedded && root!=null){embeddedPaused=false;handler.removeCallbacks(refresh);tick();}}
    /** Back from the application sidebar can first dismiss an open local recalage form. */
    boolean closeChild(){if(root!=null && calibration){showMain();return true;}return false;}
    /** Observe actual input ownership rather than equating a requested window with gained focus. */
    boolean hasInputFocus(){return root!=null && !passive && root.hasWindowFocus();}
    /** Read an opaque public WindowId for owned lifecycle qualification; it is never logged or serialized. */
    Object windowIdentity(){return root==null || embedded?null:root.getWindowId();}
    /** Report passive preparation independently of developer mode and multimedia lifetime. */
    boolean isPrepared(){return root!=null && passive && !monitoring;}
    /** Expose visible passive monitoring separately from the compact prepared badge. */
    boolean isMonitoring(){return root!=null && passive && monitoring;}
    /** Notify the existing service owner of UI-only close/standby events. */
    void onLifecycle(Runnable closed,Runnable parked){this.closed=closed;this.parked=parked;}

    /** Back and every close cross remove only this window, preserving service/session and switches. */
    void close() {
        if(root==null || closing)return;
        closing=true;handler.removeCallbacks(refresh);
        PanelRoot retiring=root;trace("CLOSING",-1,-1);retiring.held.clear();root=null;
        if(embedded){root=retiring;retiring.clearFocus();closing=false;embeddedDismiss.run();return;}
        retiring.clearFocus();retiring.setVisibility(View.GONE);
        try{host.releaseFocus(retiring,params);}catch(RuntimeException refused){/* Removal remains mandatory. */}finally{
            try{host.remove(retiring);}catch(RuntimeException alreadyDetached){/* Focus was released first. */}
            closing=false;closed.run();
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
        if(BuildConfig.TVUX_DEVELOPER_OVERLAY && passive)cross.setEnabled(false);
        heading.addView(cross,new LinearLayout.LayoutParams(dp(132),dp(52)));content.addView(heading);
        ScrollView scroll=new ScrollView(context);scroll.setFillViewport(false);
        page=new LinearLayout(context);page.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(page);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(content,new FrameLayout.LayoutParams(-1,-1));
    }

    /** Render requested switches from the same owner used by the main application's Settings. */
    private void showMain() {
        calibration=false;shell(BuildConfig.TVUX_DEVELOPER_OVERLAY?(monitoring?"Développeur · passif":"Développeur"):"SceneVibe",this::close);
        status=label("",19);page.addView(status);
        text=action("Commentaires écrits",()->{commands.text(!commands.snapshot().text);update();});
        audio=action("Commentaires audio",()->{commands.audio(!commands.snapshot().audio);update();});
        if(BuildConfig.TVUX_DEVELOPER_OVERLAY)adToggle=action("AD-SYNC",()->{commands.adSync(!commands.snapshot().adSync);update();});
        suspend=action("Suspendre les commentaires",()->{commands.suspend();feedback="Suspension demandée.";update();});
        resume=action("Reprendre les commentaires",()->{commands.resume();feedback="Reprise demandée ; confirmation MEDIA attendue.";update();});
        recalibrate=action("Recaler sur le temps du film",this::showCalibration);
        if(BuildConfig.TVUX_DEVELOPER_OVERLAY){timing=label("",15);page.addView(timing);}
        message=label(feedback,17);page.addView(message);
        page.addView(label(monitoring?"Affichage passif : touches au lecteur. Commandes et fermeture dans SceneVibe.":"Back ou × : fermer sans arrêter les commentaires.",17));
        update();
        if(!passive)text.requestFocus();
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
        if(root==null || calibration || (passive && !monitoring))return;
        Snapshot state=commands.snapshot();
        boolean available=state.active && !passive;
        presentSwitch(text,"Commentaires écrits",state.text,available);
        presentSwitch(audio,"Commentaires audio",state.audio,available);
        if(adToggle!=null)presentSwitch(adToggle,"AD-SYNC",state.adSync,state.adSyncSupported && !passive);
        if(timing!=null && (lastTimingUpdate<0 || android.os.SystemClock.elapsedRealtime()-lastTimingUpdate>=1000)){
            lastTimingUpdate=android.os.SystemClock.elapsedRealtime();setTextIfChanged(timing,state.timing);
        }
        presentAction(suspend,"Suspendre les commentaires",available);presentAction(resume,"Reprendre les commentaires",available);
        presentAction(recalibrate,"Recaler sur le temps du film",available);
        setTextIfChanged(status,(state.active?"Session Companion active":"Session Companion inactive")+"\n"+state.synchronization);
        setTextIfChanged(message,feedback);
    }

    /** Avoid repeated layout/accessibility events while the service state has not changed. */
    private void setTextIfChanged(TextView view,String value){if(!value.contentEquals(view.getText()))view.setText(value);}

    /** Project ON/OFF and availability independently, identically in the tab and interactive overlay. */
    private void presentSwitch(Button button,String title,boolean checked,boolean available) {
        setTextIfChanged(button,title+" : "+(checked?"ON":"OFF")+(BuildConfig.TVUX_DEVELOPER_OVERLAY && !available?" · indisponible":""));
        button.setEnabled(available);if(button instanceof DeveloperButton)((DeveloperButton)button).switchState(checked);
    }
    /** Actions have availability, never a fictitious permanent ON/OFF state. */
    private void presentAction(Button button,String title,boolean available) {
        setTextIfChanged(button,title+(BuildConfig.TVUX_DEVELOPER_OVERLAY && !available?" · indisponible":""));button.setEnabled(available);
    }

    /** Log only SceneVibe window policy and remote key codes; no account, content, session or third-party tree. */
    private void trace(String event,int key,int action) {
        if(BuildConfig.TVUX_DEVELOPER_OVERLAY && !embedded)Log.i("SceneVibeControls","event="+event+" mode="+(passive?"PASSIVE":"INTERACTIVE")
                +" focus="+hasInputFocus()+" flags="+(params==null?0:params.flags)+" key="+key+" action="+action);
    }

    /** Close on permission loss; refreshing a view never starts/restarts the commentary service. */
    private void tick() {
        if(root==null)return;
        if(embedded && embeddedPaused)return;
        if(!embedded && !permission.getAsBoolean()){close();return;}
        update();handler.postDelayed(refresh,250);
    }

    /** Add one full-width action with a stable focus ID. */
    private Button action(String title,Runnable work) {
        Button result=button(title,work);page.addView(result,new LinearLayout.LayoutParams(-1,dp(52)));return result;
    }

    /** Make focus visible through outline, inversion and contrast, rather than a color-only state. */
    private Button button(String title,Runnable work) {
        Button result=BuildConfig.TVUX_DEVELOPER_OVERLAY?new DeveloperButton(context):new Button(context);
        result.setId(View.generateViewId());result.setText(title);result.setTextSize(18);
        result.setAllCaps(false);result.setFocusable(true);result.setMinHeight(0);result.setMinimumHeight(0);
        result.setOnClickListener(view->work.run());
        if(BuildConfig.TVUX_DEVELOPER_OVERLAY)return result;
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
        private final SparseBooleanArray held=new SparseBooleanArray();
        /** This root is separate from every passive editorial window. */
        PanelRoot(Context context){super(context);}
        /** Consume both halves of Back so its release cannot navigate the application underneath. */
        @Override public boolean dispatchKeyEvent(KeyEvent event) {
            if(BuildConfig.TVUX_DEVELOPER_OVERLAY){
                int key=event.getKeyCode(),action=event.getAction();trace("KEY_RECEIVED",key,action);
                if(passive && !embedded){trace("PASSIVE_KEY_REFUSED",key,action);return true;}
                if(transportKey(key)){trace("TRANSPORT_CONSUMED",key,action);return true;}
                boolean tracked=key==KeyEvent.KEYCODE_BACK || key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER
                        || key==KeyEvent.KEYCODE_NUMPAD_ENTER || key==KeyEvent.KEYCODE_SPACE;
                if(tracked){
                    if(action==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)held.put(key,true);
                    else if(action==KeyEvent.ACTION_UP){
                        boolean owned=held.get(key);held.delete(key);
                        if(!owned || event.isCanceled()){trace("ORPHAN_OR_CANCELED_UP_CONSUMED",key,action);return true;}
                    }
                }
            }
            if(event.getKeyCode()==KeyEvent.KEYCODE_BACK) {
                if(event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled())back();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        /** Unexpected system-window focus loss closes the panel rather than keeping an invisible input owner. */
        @Override public void onWindowFocusChanged(boolean focused) {
            super.onWindowFocusChanged(focused);
            trace(focused?"WINDOW_FOCUS_GAINED":"WINDOW_FOCUS_LOST",-1,-1);if(!focused)held.clear();
            if(embedded)return;
            if(focused)hadFocus=true;else if(hadFocus && root==this && !passive){
                if(BuildConfig.TVUX_DEVELOPER_OVERLAY)standby();else close();
            }
        }
        /** Block platform media fallback while this explicit interactive window owns the remote. */
        private boolean transportKey(int key) {
            switch(key){
                case KeyEvent.KEYCODE_MEDIA_PLAY:case KeyEvent.KEYCODE_MEDIA_PAUSE:case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_STOP:case KeyEvent.KEYCODE_MEDIA_NEXT:case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                case KeyEvent.KEYCODE_MEDIA_REWIND:case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:case KeyEvent.KEYCODE_MEDIA_RECORD:
                case KeyEvent.KEYCODE_HEADSETHOOK:return true;
                default:return false;
            }
        }
        /** Resume only ordinary view polling on reattachment; no window or service is started. */
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(embedded && !embeddedPaused){handler.removeCallbacks(refresh);handler.postDelayed(refresh,250);}}
        /** Remove polling and ownership even if Android detaches the window unexpectedly. */
        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if(root==this){if(embedded)handler.removeCallbacks(refresh);else close();}
        }
    }
}
