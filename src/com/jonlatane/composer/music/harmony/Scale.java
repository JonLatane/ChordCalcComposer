package com.jonlatane.composer.music.harmony;

import java.util.Iterator;

import android.util.Log;
import android.util.Pair;
/**
 * Scales work like Chords but should contain more notes.
 *
 * This class may construct scales from semantic information
 * in Android Resources
 */
public class Scale extends Chord {
	private static final long serialVersionUID = -7854747114403082L;
	private static final String TAG = "Scale";

	public static class MajorScale extends Scale {
		private static final long serialVersionUID = -2497585379571977115L;

		public MajorScale(int startingNote) {
			super();
			setRoot(startingNote);
			add(startingNote);
			add(startingNote+2);
			add(startingNote+4);
			add(startingNote+5);
			add(startingNote+7);
			add(startingNote+9);
			add(startingNote+11);
			Log.i(TAG, "MajorScale");
		}
		@Override
		public boolean isMajor() {
			return true;
		}
	}
	public static class NaturalMinorScale extends Scale {
		private static final long serialVersionUID = 6308727539825345792L;

		public NaturalMinorScale(int startingNote) {
			super();
			setRoot(startingNote);
			add(startingNote);
			add(startingNote+2);
			add(startingNote+3);
			add(startingNote+5);
			add(startingNote+7);
			add(startingNote+8);
			add(startingNote+10);
		}
		
		@Override
		public boolean isMinor() {
			return false;
		}
	}
	public static class HarmonicMinorScale extends Scale {
		private static final long serialVersionUID = 1292993657552799082L;

		public HarmonicMinorScale(int startingNote) {
			super();
			setRoot(startingNote);
			add(startingNote);
			add(startingNote+2);
			add(startingNote+3);
			add(startingNote+5);
			add(startingNote+7);
			add(startingNote+8);
			add(startingNote+11);
		}
		@Override
		public boolean isMinor() {
			return false;
		}
	}
	public static class MelodicMinorScale extends Scale {
		private static final long serialVersionUID = 1509268362079826699L;
		private Scale _descMelMinor = new Scale();
		public MelodicMinorScale(int startingNote) {
			super();
			setRoot(startingNote);
			add(startingNote);
			add(startingNote+2);
			add(startingNote+3);
			add(startingNote+5);
			add(startingNote+7);
			add(startingNote+9);
			add(startingNote+11);
			
			_descMelMinor.add(startingNote);
			_descMelMinor.add(startingNote+2);
			_descMelMinor.add(startingNote+3);
			_descMelMinor.add(startingNote+5);
			_descMelMinor.add(startingNote+7);
			_descMelMinor.add(startingNote+8);
			_descMelMinor.add(startingNote+11);
		}
		@Override
		public boolean isMinor() {
			return false;
		}
		@Override
		public Scale descendingVersion() {
			return _descMelMinor;
		}
	}
	public static class ChromaticScale extends Scale {
		private static final long serialVersionUID = -1975118044575128176L;

		public ChromaticScale(int startingNote) {
			super();
			setRoot(startingNote);
			add(startingNote);
			add(startingNote+1);
			add(startingNote+2);
			add(startingNote+3);
			add(startingNote+4);
			add(startingNote+5);
			add(startingNote+6);
			add(startingNote+7);
			add(startingNote+8);
			add(startingNote+9);
			add(startingNote+10);
			add(startingNote+11);
		}
	}
	
	
	public Scale() {
		super();
		Log.i(TAG,"Scale()");
	}

	public Scale(Chord c) {
		super(c);
	}
	
	/**
	 * May be overridden by melodic minor or bebop scales.  Otherwise return this.
	 * 
	 * @return
	 */
	public Scale descendingVersion() {
		return this;
	}
	
	/**
	 * Returns the chromatic note for the provided scale degree of this Scale.
	 * For C Major, the parameters 1, 2, 3, 4, 5, 6, 7 return 0, 2, 4, 5, 7, 9, 11.
	 * 
	 * @param i
	 * @return
	 */
	public int getDegree(int i) {
		i = HEPTATONIC.mod(i);
		if(i == 0) {
			i = 7;
		}
		int currentDegree = 1;
		Iterator<Integer> itr = tailSet(getRoot()).iterator();
		while(true) {
			if(itr.hasNext()) {
				if(currentDegree == i) {
					return itr.next();
				} else {
					itr.next();
				}
			} else {
				itr = iterator();
			}
			currentDegree = HEPTATONIC.mod(currentDegree + 1);
			if(currentDegree == 0) {
				currentDegree = 7;
			}
		}
	}
	
	/**
	 * Returns a pair of scale degrees to the left and right of i.  If i is in this Scale,
	 * the two results will be the same.  If not, each will be the scale degrees to the right
	 * and left of i.
	 * 
	 * @param i
	 * @return a Pair of numbers between 1 and size()
	 */
	public Pair<Integer, Integer> degreeOf(int i) {
		i = TWELVETONE.mod(i);
		
		Integer upper = ceiling(i);
		if( upper == null )
			upper = ceiling(i-12);
		Integer lower = floor(i);
		if( lower == null )
			lower = floor(i+12);
		int chromatic = getRoot();
		int degree = 0;
		Integer lowerDegree = null;
		Integer upperDegree = null;
		while(lowerDegree == null || upperDegree == null) {
			if(contains(chromatic))
				degree += 1;
			if(chromatic == lower)
				lowerDegree = degree;
			if(chromatic == upper)
				upperDegree = degree;
			chromatic += 1;
			
			//degree = TWELVETONE.mod(degree);
			chromatic = TWELVETONE.mod(chromatic);
		}
		
		return new Pair<Integer,Integer>( lowerDegree, upperDegree );
	}
		
	//returns 0 if the chord is tonic, 2 if it is a ii/II, 4 for a iii/III, 5 for a iv/IV, etc.
	// bounds are constrained by the modulus
	public int rootFunction(Chord c) {
		return MODULUS.mod(c.getRoot() - getRoot());
	}

	public boolean isMajor() {
		return false;
	}
	
	public boolean isMinor() {
		return false;
	}
	
	public static Scale fromChord(Chord c) {
		//TODO
		return null;
	}
	
}
