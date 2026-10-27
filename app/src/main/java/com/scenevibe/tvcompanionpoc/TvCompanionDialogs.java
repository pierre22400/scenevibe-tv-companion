package com.scenevibe.tvcompanionpoc;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shared non-destructive dismissal for SceneVibe application modals.
 * Android dispatches Back to the dialog before the underlying Activity.
 * The visible, remote-focusable cross calls cancel, retaining the existing
 * dismissal/focus callbacks and never invoking a positive business action.
 * Historical variants keep their original Android dialog builder.
 */
public final class TvCompanionDialogs {
    /** Enable the new close affordance only for the experimental TV-UX candidate. */
    public static AlertDialog.Builder builder(Activity activity) {
        return BuildConfig.TVUX_CONTROL_PANEL?new CloseableBuilder(activity):new AlertDialog.Builder(activity);
    }

    /** Preserve every existing builder callback and Android's normal Back behavior. */
    private static final class CloseableBuilder extends AlertDialog.Builder {
        private final Activity activity;
        private CharSequence title="";
        /** Retain the Activity's native dialog theme without an extra overlay window. */
        CloseableBuilder(Activity activity){super(activity);this.activity=activity;}
        /** Keep the existing textual title so a focusable close button can share its header. */
        @Override public AlertDialog.Builder setTitle(CharSequence title){this.title=title;return super.setTitle(title);}
        /** Resolve resource titles with the same Activity locale. */
        @Override public AlertDialog.Builder setTitle(int title){return setTitle(activity.getText(title));}
        /** Add only a cancel action; positive/negative actions and dismissal listeners remain unchanged. */
        @Override public AlertDialog show() {
            AlertDialog dialog=super.create();
            LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);header.setBackgroundColor(Color.WHITE);
            TextView label=new TextView(activity);label.setText(title);label.setTextColor(Color.BLACK);label.setTextSize(22);
            int pad=Math.round(12*activity.getResources().getDisplayMetrics().density);label.setPadding(pad,pad,pad,pad);
            header.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            Button cross=new Button(activity);cross.setText("× Fermer");cross.setTextSize(18);cross.setAllCaps(false);
            cross.setContentDescription("Fermer la fenêtre");cross.setFocusable(true);cross.setOnClickListener(view->dialog.cancel());
            header.addView(cross);dialog.setCustomTitle(header);dialog.setCancelable(true);dialog.setCanceledOnTouchOutside(false);
            dialog.show();return dialog;
        }
    }

    /** This utility never owns a dialog or commentary session. */
    private TvCompanionDialogs() {}
}
