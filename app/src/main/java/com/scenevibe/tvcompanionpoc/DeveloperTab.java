package com.scenevibe.tvcompanionpoc;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;

/**
 * The normal Activity's Developer tab, not an overlay Activity.
 * Preparation and a normal explicit player launch are separate actions, so the
 * service window cannot take D-pad focus while the user is choosing the player.
 * All business widgets are the same CompanionControlPanel view/command port.
 */
final class DeveloperTab {
    private final MainActivity activity;
    private final LinearLayout root;
    private final CompanionControlPanel controls;
    private final TextView state,result;
    private final Button mode,prepare,launch;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable poll=this::tick;

    /** Construct ordinary Activity views; this creates no service, window or media admission. */
    DeveloperTab(MainActivity activity,Runnable dismiss) {
        this.activity=activity;root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(android.graphics.Color.WHITE);root.setPadding(dp(12),dp(12),dp(12),dp(12));
        state=label("Mode Développeur désactivé.");root.addView(state);
        result=label("Prépare le panneau, puis utilise Passer à Prime Video. Aucun raccourci global n’est nécessaire.");
        mode=button("Mode Développeur : OFF",()->{
            boolean enabled=TvFirst009TextServiceOwner.developerStage()!=DeveloperOverlayState.Stage.OFF;
            result.setText(TvFirst009TextServiceOwner.setDeveloperMode(activity,!enabled)?"Mode mis à jour.":"Activation du mode refusée.");refresh();
        });
        prepare=button("Afficher les commandes en overlay",()->{
            result.setText(TvFirst009TextServiceOwner.prepareDeveloperPanel(activity)
                    ?"Préparation demandée ; les touches restent dans l’application."
                    :"Préparation refusée : active le mode et l’autorisation de superposition.");refresh();
        });
        launch=button("Passer à Prime Video",()->{
            result.setText(activity.launchDeveloperPrime()?"Ouverture normale de Prime demandée.":"Prime indisponible ou panneau non préparé.");refresh();
        });
        root.addView(result);
        controls=new CompanionControlPanel(activity,TvFirst009TextServiceOwner.panelCommands());
        root.addView(controls.embeddedView(dismiss));
        refresh();
    }

    /** Expose an ordinary subtree of the existing application navigation. */
    LinearLayout view(){return root;}
    /** Read only the owner; the tab has no duplicate TEXT/AUDIO/AD-SYNC preferences. */
    void refresh() {
        DeveloperOverlayState.Stage stage=TvFirst009TextServiceOwner.developerStage();
        setText(mode,"Mode Développeur : "+(stage==DeveloperOverlayState.Stage.OFF?"OFF":"ON"));
        setText(state,TvFirst009TextServiceOwner.developerStatus());
        prepare.setEnabled(stage!=DeveloperOverlayState.Stage.OFF && stage!=DeveloperOverlayState.Stage.HANDOFF && stage!=DeveloperOverlayState.Stage.WAITING_PLAYER);
        launch.setEnabled(stage==DeveloperOverlayState.Stage.PREPARED && activity.developerPrimeAvailable());
    }
    /** The Activity can close a recalage form before changing its normal navigation page. */
    boolean closeChild(){return controls.closeChild();}
    /** Resume presentation only; no panel preparation or playback starts here. */
    void resume(){handler.removeCallbacks(poll);controls.resumeEmbedded();tick();}
    /** Stop both Activity-owned display refreshers while the service keeps its own lifetime. */
    void pause(){handler.removeCallbacks(poll);controls.pauseEmbedded();}
    /** Discard only this tab's transient view work. */
    void close(){pause();}
    /** Refresh state without becoming a media clock or an automatic focus transition. */
    private void tick(){refresh();handler.postDelayed(poll,500);}
    /** Prevent unchanged status from generating repeated layout/accessibility events. */
    private void setText(TextView view,String text){if(!text.contentEquals(view.getText()))view.setText(text);}
    /** Use remote-focusable native buttons, with literal labels for TV navigation. */
    private Button button(String title,Runnable action) {
        Button button=new Button(activity);button.setText(title);button.setTextSize(18);button.setAllCaps(false);
        button.setFocusable(true);button.setOnClickListener(view->action.run());root.addView(button,new LinearLayout.LayoutParams(-1,dp(54)));return button;
    }
    /** Keep explanatory text legible on a light, opaque developer surface. */
    private TextView label(String text){TextView view=new TextView(activity);view.setText(text);view.setTextColor(android.graphics.Color.BLACK);view.setTextSize(18);return view;}
    /** Scale dimensions rather than assuming a specific Sony pixel density. */
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}
