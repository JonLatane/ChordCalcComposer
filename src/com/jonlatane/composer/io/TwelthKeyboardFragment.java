package com.jonlatane.composer.io;

import android.animation.IntEvaluator;
import android.animation.ValueAnimator;
import android.app.Fragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jonlatane.composer.R;
import com.jonlatane.composer.audio.AudioTrackGenerator;
import com.jonlatane.composer.music.harmony.Chord;
import com.jonlatane.composer.music.harmony.Key;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * This is the widget for the keyboard input system.  This is a Fragment that can be added to the bottom of any layout to provide
 * a hideable keyboard overlay.  Some of its features include melodic (single-note) input, chord-guessing harmonic input (associating,
 * essentially, combinations of keys with -/M7 "characteristics" to guess "names" of chords.
 * @author Jon Latane
 *
 */
public class TwelthKeyboardFragment extends Fragment {
	private static final int[] _slots = {R.id.bestChord, R.id.second, R.id.third, R.id.fourth, R.id.fifth, R.id.sixth, R.id.seventh, R.id.eighth, R.id.ninth, R.id.tenth, R.id.eleventh, R.id.twelfth};
	private static final String TAG = "TwelthKeyboardFragment";

	private Integer _initialRhythmAreaWidth;
	private KeyboardScroller _keyboardScroller;
    private Collection<View> _linkedViews = new LinkedList<View>();

	public KeyboardScroller getKeyboardScroller() {
		return _keyboardScroller;
	}

	private KeyboardIOHandler kbdIO;
	private HorizontalScrollView _chordScroller;
	private Key _keyToNameFrom = Key.CMajor;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
        // Inflate the layout for this fragment
        final View result = inflater.inflate(R.layout.twelthkeyboard, container, false);
        
        //Set up basic stuff from layout
        _initialRhythmAreaWidth = result.findViewById(R.id.rhythmButtonArea).getLayoutParams().width;
        _keyboardScroller = (KeyboardScroller)result.findViewById(R.id.kbScroller);
		kbdIO = new KeyboardIOHandler(this, result);
		kbdIO.harmonicModeOn();
		_chordScroller = (HorizontalScrollView)result.findViewById(R.id.chordScroller);

        //Use a properly-rendering font for the chord display area
        Typeface face=Typeface.createFromAsset(result.getContext().getAssets(), "fonts/DroidSansFallback.ttf");
        for(int i : _slots) {
            final TextView tv = (TextView) result.findViewById(i);
			tv.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View view) {
					ClipboardManager clipboard = (ClipboardManager) (getActivity().getSystemService(Context.CLIPBOARD_SERVICE));
					ClipData clip = ClipData.newPlainText("ChordCalc Chord", tv.getText());
					clipboard.setPrimaryClip(clip);
					Toast toast = Toast.makeText(getActivity(), R.string.chord_copied_to_clipboard, Toast.LENGTH_SHORT);
					toast.show();
					return true;
				}
			});
            tv.setTypeface(face);
        }

        //Set up the keyboardScroller itself
		_keyboardScroller = (KeyboardScroller)result.findViewById(R.id.kbScroller);

        return result;
    }
	
	// Keep track of when chord updates are started so only the most recent operation will update the views
	private static long mostRecentUCDInitializationTime;
	private class UpdateChordDisplay extends AsyncTask<Chord, Integer, List<String>> {
		TreeMap<Integer,List<String>> data;
		private long myInitializationTime;
		
		
		public UpdateChordDisplay() {
			myInitializationTime = System.currentTimeMillis();
			mostRecentUCDInitializationTime = myInitializationTime;
		}
		
		@Override
		protected List<String> doInBackground(Chord... c) {
			data = Key.getRootLikelihoodsAndNames(Key.CChromatic, c[0], _keyToNameFrom);
			List<String> result = new LinkedList<String>();
			for(Map.Entry<Integer,List<String>> e : data.descendingMap().entrySet() ) {
				for( String s : e.getValue() ) {
					result.add(s);
				}
			}
			return result;
		}

		@Override
		protected void onPostExecute(List<String> values) {
			int idx = 0;
			if(mostRecentUCDInitializationTime == myInitializationTime) {
				for( String s : values ) {
					if( idx >= _slots.length) break;
					
					TextView v = (TextView)getView().findViewById(_slots[idx++]);
					v.setText(s.toString());
				}
				_chordScroller.scrollTo(0,0);
			}
		}


	 }
	public void updateChordDisplay() {
		new UpdateChordDisplay().execute(kbdIO.getChord());
	}
	
	private class WidthEvaluator extends IntEvaluator {

	    private View v;
	    public WidthEvaluator(View v) {
	        this.v = v;
	    }

		@Override
	    public Integer evaluate(float fraction, Integer startValue, Integer endValue) {
	        int num = (Integer)super.evaluate(fraction, startValue, endValue);
	        ViewGroup.LayoutParams params = v.getLayoutParams();
	        params.width = num;
	        v.setLayoutParams(params);
	        return num;
	    }
	}
	
	public AudioTrackGenerator getTrackGenerator() {
		return kbdIO.trackGenerator;
	}

	public boolean rhythmicModeIsEnabled() {
		LinearLayout l = (LinearLayout)getView().findViewById(R.id.rhythmButtonArea);
		boolean result = l.getWidth() != 0;
		Log.i(TAG,"rhythmicModeIsEnabled:"+result);
		return result;
	}
	
	private void enableRhythmicMode(View v) {
		final LinearLayout l = (LinearLayout)getView().findViewById(R.id.rhythmButtonArea);
		ValueAnimator.ofObject(new WidthEvaluator(l), l.getWidth(), _initialRhythmAreaWidth).start();
	}
	
	public void enableRhythmicMode() {
		//final LinearLayout l = (LinearLayout)getView().findViewById(R.id.rhythmButtonArea);
		//ValueAnimator.ofObject(new WidthEvaluator(l), l.getWidth(), _initialRhythmAreaWidth).start();
		enableRhythmicMode(getView());
	}
	private void disableRhythmicMode(View v) {
		final LinearLayout l = (LinearLayout)v.findViewById(R.id.rhythmButtonArea);
		ValueAnimator.ofObject(new WidthEvaluator(l), l.getWidth(), 0).start();
	}
	public void disableRhythmicMode() {
		//final LinearLayout l = (LinearLayout)getView().findViewById(R.id.rhythmButtonArea);
		//ValueAnimator.ofObject(new WidthEvaluator(l), l.getWidth(), 0).start();
		disableRhythmicMode(getView());
	}
	public boolean toggleRhythmicMode() {
		if(rhythmicModeIsEnabled())
			disableRhythmicMode();
		else
			enableRhythmicMode();
		return rhythmicModeIsEnabled();
	}
	
	private void enableHarmonicMode(View r) {
		final View v = r.findViewById(R.id.chordScroller);
		v.animate().translationY(0);
		kbdIO.harmonicModeOn();
	}
	public void enableHarmonicMode() {
		//final View v = getView().findViewById(R.id.chordScroller);
		//v.animate().translationY(0);
		//_myKbdIO.harmonicModeOn();
		enableHarmonicMode(getView());
	}
	
	private void disableHarmonicMode(View r) {
		final View v = r.findViewById(R.id.chordScroller);
		v.animate().translationY(v.getHeight());
		kbdIO.harmonicModeOff();
	}
	public void disableHarmonicMode() {
		//final View v = getView().findViewById(R.id.chordScroller);
		//v.animate().translationY(v.getHeight());
		//_myKbdIO.harmonicModeOff();
		disableHarmonicMode(getView());
	}
	public boolean toggleHarmonicMode() {
		//if(getView().findViewById(R.id.chordScroller).getHeight() != 0)
		if(kbdIO.isHarmonic())
			disableHarmonicMode();
		else
			enableHarmonicMode();
		return kbdIO.isHarmonic();
	}

    /**
     * Allows hiding/showing to stack views atop the keyboard.  The first view to be linked should
     * be the view most directly above the Fragment.
     *
     * @param v
     */
    public void linkView(View v) {
        _linkedViews.add(v);
    }
    public void unlinkView(View v) {
        _linkedViews.remove(v);
    }

	public boolean keyboardIsEnabled() {
		return getView().getTranslationY() == 0;
	}
	public void hideKeyboardFragment() {
		final View v = getView();
        int netHeight = v.getHeight();
		v.animate().translationY(netHeight);
        for(View linked : _linkedViews) {
            netHeight += linked.getHeight();
            linked.animate().translationY(netHeight);
        }
	}
	public void showKeyboardFragment() {
		final View v = getView();
		v.animate().translationY(0);
//        for(View linked : _linkedViews) {
//            linked.animate().translationY(0);
//        }
	}

	public void toggleKeyboardFragment() {
		if(!keyboardIsEnabled())
			showKeyboardFragment();
		else
			hideKeyboardFragment();
	}
	
	public void highlightChord(Chord c) {
		kbdIO.setHarmonicChord(c);
	}
	public void clearChordHighlights() {
		kbdIO.setHarmonicChord(null);
	}

	public Key getKeyToNameFrom() {
		return _keyToNameFrom;
	}

	public void setKeyToNameFrom(Key _keyToNameFrom) {
		this._keyToNameFrom = _keyToNameFrom;
	}
}
