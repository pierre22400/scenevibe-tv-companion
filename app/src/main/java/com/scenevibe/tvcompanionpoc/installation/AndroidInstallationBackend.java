package com.scenevibe.tvcompanionpoc.installation;

import android.content.Context;
import android.content.SharedPreferences;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;

/**
 * Sole Android persistence owner for the existing private cloud_track preference file.
 * One editor/commit publishes a batch. Android may update its memory cache even when disk
 * commit returns false: a shared fixed-key prior view masks that failed publication until
 * recovery can restage it in the next single batch. No rollback write or runtime occurs.
 */
public final class AndroidInstallationBackend implements InstallationStore.Backend {
    private static final String[] KEYS={InstallationStore.SNAPSHOT_KEY,"revision","runtime","manifest","ackRevision"};
    private static final List<FailedView> FAILED_VIEWS=new ArrayList<>();
    private final SharedPreferences preferences;

    /** Keep the exact historical file and private mode, without accessing identity or credentials. */
    public AndroidInstallationBackend(Context context) {
        if (context==null) throw new IllegalArgumentException("Missing installation context");
        preferences=context.getApplicationContext().getSharedPreferences("cloud_track",Context.MODE_PRIVATE);
    }
    /** Use the shared preferences identity to serialize all adapters for the same file. */
    @Override public Object monitor() {return preferences;}

    /** Read only known keys, retaining malformed-type rejection after a failed publication. */
    @Override public String get(String key) {
        requireKey(key);
        synchronized (preferences) {
            PriorView failed=failedView();
            if (failed==null) {
                try {return preferences.getString(key,null);}
                catch (ClassCastException invalidType) {throw new IllegalStateException("Invalid preference type");}
            }
            if (failed.corrupt.contains(key)) throw new IllegalStateException("Invalid preference type");
            return failed.values.get(key);
        }
    }

    /** Commit one fixed-key batch; on failure preserve the prior view without a second editor/write. */
    @Override public boolean commit(Map<String,String> values,Set<String> removed,boolean clear) {
        if (values==null||removed==null||values.size()>KEYS.length||removed.size()>KEYS.length)
            throw new IllegalArgumentException("Invalid preference batch");
        for (Map.Entry<String,String> value:values.entrySet()) {
            requireKey(value.getKey());
            if (value.getValue()==null||removed.contains(value.getKey()))
                throw new IllegalArgumentException("Invalid preference value");
        }
        for (String key:removed) requireKey(key);
        synchronized (preferences) {
            PriorView failed=failedView(),prior=failed==null?capture():failed;
            if (!clear) for (String key:prior.corrupt)
                if (!values.containsKey(key)&&!removed.contains(key)) return false;
            try {
                SharedPreferences.Editor editor=preferences.edit();
                if (clear) editor.clear();
                else if (failed!=null) for (String key:KEYS) {
                    String previous=prior.values.get(key);
                    if (previous==null) editor.remove(key);else editor.putString(key,previous);
                }
                for (Map.Entry<String,String> value:values.entrySet()) editor.putString(value.getKey(),value.getValue());
                for (String key:removed) editor.remove(key);
                if (editor.commit()) {
                    setFailedView(null);
                    return true;
                }
            } catch (RuntimeException failedCommit) { /* No content or exception reaches diagnostics. */ }
            setFailedView(prior);
            return false;
        }
    }

    /** Capture five immutable string references only; malformed preference types retain a closed flag. */
    private PriorView capture() {
        Map<String,String> values=new HashMap<>();Set<String> corrupt=new HashSet<>();
        for (String key:KEYS) {
            try {values.put(key,preferences.getString(key,null));}
            catch (ClassCastException invalidType) {corrupt.add(key);}
        }
        return new PriorView(values,corrupt);
    }
    /** Resolve process-local durability protection shared across repository/store recreation. */
    private PriorView failedView() {
        synchronized (FAILED_VIEWS) {
            for (int i=FAILED_VIEWS.size()-1;i>=0;i--) {
                FailedView record=FAILED_VIEWS.get(i);
                SharedPreferences owner=record.owner.get();
                if (owner==null) FAILED_VIEWS.remove(i);
                else if (owner==preferences) return record.view;
            }
            return null;
        }
    }
    /** Replace a weak identity binding without invoking a preference proxy's hashCode/equals. */
    private void setFailedView(PriorView view) {
        synchronized (FAILED_VIEWS) {
            for (int i=FAILED_VIEWS.size()-1;i>=0;i--) {
                SharedPreferences owner=FAILED_VIEWS.get(i).owner.get();
                if (owner==null||owner==preferences) FAILED_VIEWS.remove(i);
            }
            if (view!=null) FAILED_VIEWS.add(new FailedView(preferences,view));
        }
    }
    /** Limit all persistence access to the installation file's fixed known key vocabulary. */
    private static void requireKey(String key) {
        for (String known:KEYS) if (known.equals(key)) return;
        throw new IllegalArgumentException("Invalid installation preference key");
    }
    /** Immutable bounded prior view contains no preference/service/network object. */
    private static final class PriorView {
        final Map<String,String> values;
        final Set<String> corrupt;
        /** Freeze at most the five captured keys and type flags, without copying any payload bytes. */
        PriorView(Map<String,String> values,Set<String> corrupt) {
            this.values=Collections.unmodifiableMap(values);this.corrupt=Collections.unmodifiableSet(corrupt);
        }
    }
    /** Weak file identity does not retain a Context/preferences lifetime or call foreign equality code. */
    private static final class FailedView {
        final WeakReference<SharedPreferences> owner;
        final PriorView view;
        /** Hold only a weak identity and the bounded immutable durability view. */
        FailedView(SharedPreferences preferences,PriorView view) {
            this.owner=new WeakReference<>(preferences);this.view=view;
        }
    }
}
