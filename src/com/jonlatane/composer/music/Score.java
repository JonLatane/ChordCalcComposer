package com.jonlatane.composer.music;

import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.jonlatane.composer.music.Score.Staff.StaffDelta;
import com.jonlatane.composer.music.Score.Staff.Voice;
import com.jonlatane.composer.music.Score.Staff.Voice.VoiceDelta;
import com.jonlatane.composer.music.coverings.Clef;
import com.jonlatane.composer.music.coverings.TimeSignature;
import com.jonlatane.composer.music.harmony.Chord;
import com.jonlatane.composer.music.harmony.Enharmonics;
import com.jonlatane.composer.music.harmony.Key;
import com.jonlatane.composer.music.harmony.PitchSet;
import com.jonlatane.composer.music.harmony.Scale;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A Score is an object that is most usefully accessed/rendered via ScoreDeltas through its iterator.  It
 * represents a piece of music as a layered map from Rationals to PitchSets, Articulations, Dynamics,
 * Chords, Scales, Keys, and Meters.  Some of this information is contained in Staves and some in Voices,
 * however the most useful means of accessing it is through the @ScoreDelta class.
 *   
 * @author Jon
 *
 */
public class Score {
	private static final String TAG = "Score";
	private Meter _meter = new Meter();
	private Rational _fine;
	private Staff[] _staves = new Staff[0];
	
	/**
	 * Create a 4-bar score for the given TimeSignature.  The Time Signature is added at Rational point 1,
	 * so the downbeat of the first measure is at Rational.ONE.
	 * 
	 * @param ts
	 */
	public Score(TimeSignature ts) {
		this(ts, 4);
	}
	
	/**
	 * Create a Score containing the requested number of bars for the given time signature. The Time Signature
	 * is added at Rational point 1, so the downbeat of the first measure is at Rational.ONE.
	 * 
	 * @param ts
	 * @param numBars
	 */
	public Score(TimeSignature ts, Integer numBars) {
		this(ts, numBars, Rational.ZERO);
	}
	
	/**
	 * This is not particularly useful except as a boundary case for the model for pickup != 0.  However,
	 * a dotted-sixteenth pickup can be handled by the meter pretty elegantly, though all your downbeats
	 * end up occurring at r = i + 3/8 in the model the Meter finds them at 1, 2, 3, 4 properly.
	 * 
	 * The downbeat of the first measure is at Rational.ONE + pickup, the location of the TimeSignature
	 * within the Meter.
	 * 
	 * @param ts
	 * @param numBars
	 * @param pickup
	 */
	public Score(TimeSignature ts, Integer numBars, Rational pickup) {
		assert(numBars > 0);
		setFine(new Rational(1 + numBars * ts.TOP, 1));
		_meter.put(pickup.plus(Rational.ONE), ts);
	}
	

	public Rational getFine() {
		return _fine;
	}
	
	/**
	 * Lengthen or shorten your Score in the "best possible" way.
	 * 
	 * @param r
	 */
	public void setFine(Rational r) {
		if(_fine == null)
			_fine = r;
		// Put rests and No Chord if we're increasing the length and nothing is defined there.
		// This way if you accidentally shorten it, you can re-lengthen it to restore data lost,
		// but if you lengthen it you don't get annoying tied whole notes everywhere (though they're
		// still only one PitchSet).
		if(r.compareTo(_fine) > 0) {
			for(Staff s : _staves) {
				if(s._chords._data.navigableKeySet().tailSet(_fine, false).size() == 0) {
					s._chords._data.put(_fine, Chord.NO_CHORD);
				}
				for(Staff.Voice v : s._voices) {
					if(v._notes._data.navigableKeySet().tailSet(_fine, false).size() == 0) {
						v._notes._data.put(_fine, PitchSet.REST);
					}
				}
			}
		}
		_fine = r;
	}
	
	/**
	 * A Staff is in every way like a Score in terms of its mapping behavior, but it does not store a Meter.
	 * That is to say, everything but the time signatures in a piece of music are defined at the Staff level.
	 * 
	 * Moreover, a staff is the most "useful" chunk of information in a score.  It contains harmony.  A Voice
	 * only contains a PitchSet (its NOTES), Articulation and Dynamics, while information such as the given
	 * Chord and Scale is held to be constant within a single Staff.
	 * 
	 * Merging Voices into Staves requires resolution of conflicts that may arise as a result of differences
	 * in Chords, Keys, or Clefs.
	 * 
	 * @author Jon Latane
	 *
	 */
	public class Staff {
		private RhythmMap<Chord> _chords = new RhythmMap<Chord>();
		private RhythmMap<Key> _keys = new RhythmMap<Key>();
		private RhythmMap<Clef> _clefs = new RhythmMap<Clef>();
		private RhythmMap<Scale> _scales = new RhythmMap<Scale>();
		public int TRANSPOSITION = 0;
		public String TITLE = "";
		private Voice[] _voices = new Voice[0];
		
		/**
		 * Access Staves through the newStaff method of Score.
		 */
		private Staff() {
			this(Clef.treble());
		}
		
		private Staff(Clef c) {
			_clefs.setDefaultValue(c);
			_chords.setDefaultValue(Chord.NO_CHORD);
			_keys.setDefaultValue(Key.CMajor);
		}
		
		/**
		 * As stated in @Staff, a Voice only contains a PitchSet (its NOTES), Articulation and Dynamics, while 
		 * information such as the given Chord, Key and Scale is held to be constant within a single Staff.
		 * 
		 * While a Voice does have VoiceDeltas and voiceDeltaAt(r) method, it is not iterable.  Iterating through
		 * a Voice alone is not particularly useful to us and is identical to iterating through a single-voiced
		 * {@link: Staff}.
		 * 
		 * @author Jon Latane
		 *
		 */
		public class Voice {
			private RhythmMap<PitchSet> _notes = new RhythmMap<PitchSet>();
			
			/**
			 * Access Voices through the newVoice method of Staff.
			 */
			private Voice() {
				_notes.setDefaultValue(PitchSet.REST);
			}
			
			public class VoiceDelta {
				public Rational LOCATION = null;
				
				/**
				 * A simple class for storing pointers to things in a Voice.
				 * @author Jon Latane
				 *
				 */
				public class VoiceDeltaStuff {
					public PitchSet NOTES = null;
					public Rational TIME = null;
                    @Override
                    public String toString() {
                        StringBuffer result = new StringBuffer("{");
                        result.append(NOTES);
                        result.append("@");
                        result.append(TIME);
                        return result.toString();
                    }
				}
				
				public VoiceDeltaStuff ESTABLISHED = new VoiceDeltaStuff();
				public VoiceDeltaStuff CHANGED = new VoiceDeltaStuff();
				
				public Rational getNoteheadAtLocation() {
					Rational result = null;
					if(CHANGED.NOTES != null) {
						result = CHANGED.NOTES.tying[0];
					} else if(ESTABLISHED.NOTES != null) {
						for(int idx = 0; idx < ESTABLISHED.NOTES.tying.length - 1; idx++){
							Rational r = ESTABLISHED.TIME.plus(ESTABLISHED.NOTES.tying[idx]);
							if(LOCATION.equals(r)) {
								result = ESTABLISHED.NOTES.tying[idx + 1] .minus( ESTABLISHED.NOTES.tying[idx] );
							}
						}
					}
					
					return result;
				}

                @Override
                public String toString() {
                    StringBuffer result = new StringBuffer();
                    result.append("CHANGED:");
                    result.append(CHANGED);
                    result.append("\n");
                    result.append("ESTABLISHED:");
                    result.append(ESTABLISHED);
                    return result.toString();
                }
			}
			
			public VoiceDelta voiceDeltaAt(Rational r) {
				VoiceDelta result = new VoiceDelta();
				result.LOCATION = r;
				result.ESTABLISHED.NOTES = _notes.getObjectAt(r);
				result.ESTABLISHED.TIME = _notes._data.floorKey(r);
				
				result.CHANGED.NOTES = _notes._data.get(r);
				if(result.CHANGED.NOTES != null)
					result.CHANGED.TIME = r;
				
				return result;
			}
		}
		
		/**
		 * A StaffDelta is a collection of information about what has changed and what has remained constant
		 * at a given point in time.  It is most usefully accessed through StaffDelta iterators which allow
		 * us to iterate through scores.
		 * 
		 * @author Jon
		 */
		public class StaffDelta {
			/**
			 * This points to the location of this StaffDelta.  getStaffDeltaAt(LOCATION)
			 * should return an identical (or more up-to-date) version of this Staff.
			 */
			public Rational LOCATION = null;
			public boolean IS_LAST_IN_MEASURE = false;
			
			/**
			 * If the Key changes, our rendering layer needs to group this always
			 * with the preceding StaffDelta.
			 */
			public Key KEY_CHANGE_AFTER = null;
			public Clef CLEF_CHANGE_AFTER = null;
			
			/**
			 * A simple class for storing pointers to things in a Staff.
			 * @author Jon Latane
			 *
			 */
			public class StaffDeltaStuff {
				public Clef CLEF = null;
				public Key KEY = null;
				public Chord CHORD = null;
                @Override
                public String toString() {
                    StringBuffer result = new StringBuffer();
                    result.append("CLEF: ");
                    result.append(CLEF);
                    result.append("\n");
                    result.append("KEY: ");
                    result.append(KEY);
                    result.append("\n");
                    result.append("CHORD: ");
                    result.append(CHORD);
                    return result.toString();
                }
			}
			
			public StaffDeltaStuff ESTABLISHED = new StaffDeltaStuff();
			public StaffDeltaStuff CHANGED = new StaffDeltaStuff();
			
			public Voice.VoiceDelta[] VOICES;
            @Override
            public String toString() {
                StringBuffer result = new StringBuffer();
                result.append("CHANGED:\n");
                result.append(CHANGED);
                result.append("\n\n");
                result.append("ESTABLISHED:\n");
                result.append(ESTABLISHED);
                result.append("\n\n");
                result.append("VOICES:\n");
                result.append(Arrays.toString(VOICES));
                return result.toString();
            }
		}
		
		/**
		 * The most useful means of accessing a StaffDelta.
		 * 
		 * @param r
		 * @return
		 */
		public StaffDelta staffDeltaAt(Rational r) {
			StaffDelta result = new StaffDelta();
			
			result.LOCATION = r;
			
			Rational nextInOverallRhythm = getOverallRhythm().higher(r);
			if( nextInOverallRhythm != null ) {
				result.KEY_CHANGE_AFTER = _keys._data.get(nextInOverallRhythm);
				result.CLEF_CHANGE_AFTER = _clefs._data.get(nextInOverallRhythm);
			}
			
			result.ESTABLISHED.CLEF = _clefs.getObjectAt(r);
			result.ESTABLISHED.KEY = _keys.getObjectAt(r);
			result.ESTABLISHED.CHORD = _chords.getObjectAt(r);
			
			result.CHANGED.CLEF = _clefs._data.get(r);
			result.CHANGED.KEY = _keys._data.get(r);
			result.CHANGED.CHORD = _chords._data.get(r);
			
			result.VOICES = new Voice.VoiceDelta[_voices.length];
			for(int i = 0; i < _voices.length; i++) {
				Voice v = _voices[i];
				result.VOICES[i] = v.voiceDeltaAt(r);
			}
			
			return result;
		}

		public Voice[] getVoices() {
			return _voices;
		}
		

		public Voice getVoice(int n) {
			return _voices[n];
		}
		
		public int getNumVoices() {
			return _voices.length;
		}
		
		/**
		 * Try to pick the best chord name for the given passage.  
		 *
		 */
		public TreeMap<Integer,List<String>> guessChord(Rational start, Rational end, int preferredCharacteristicLength) {
			TreeMap<Integer,List<String>> result = new TreeMap<Integer,List<String>>();
			
			// Track which notes occur most frequently at "felt" moments
			SparseIntArray noteOccurences = new SparseIntArray();
			for(Iterator<StaffDelta> itr = staffIterator(start); itr.hasNext(); ) {
				StaffDelta sd = itr.next();
				if(sd.LOCATION.compareTo(end) > 0)
					break;
				for(Voice.VoiceDelta vd : sd.VOICES) {
					for(int n : vd.ESTABLISHED.NOTES) {
						int nClass = Chord.TWELVETONE.mod(n);
						int occurences = noteOccurences.get(nClass);
						noteOccurences.put(nClass, ++occurences);
					}
				}
			}
			
			// Assume the whole passage is in the starting key, or C Major as a fallback
			Key k = _keys.getObjectAt(start);
			if( k == null )
				k = Key.CMajor;
			
			// Keep track of names given for each chord.  When we find the chord, we will choose the modal name
			// for it.
			int[] rootScores = new int[12];
			SparseArray<LinkedList<String>> namesLists = new SparseArray<LinkedList<String>>();
			for(int i = 0; i < 12; i++) {
				namesLists.put(i, new LinkedList<String>());
				rootScores[i] = 0;
			}
			
			// Analyze by all notes, then remove those less frequently used.  Choose the modal name
			// for the best root found.  This is the "heart" of the method - everything before is setup.
			while(noteOccurences.size() > 0) {
				Chord c = new Chord();
				
				// Add all notes to the Chord.  Eliminate the note with the least presses.
				int currentSize = noteOccurences.size();
				while( noteOccurences.size() == currentSize ) {
					for(int i = 0; i < noteOccurences.size(); i++) {
						int key = noteOccurences.keyAt(i);
						int val = noteOccurences.valueAt(i) - 1;
						
						c.add(key);
						
						if(val < 1)
							noteOccurences.removeAt(i--);
						else
							noteOccurences.put(key,val);
					}
				}
				
				// Analyze the Chord we've created
				TreeMap<Integer,List<String>> thisResult = Key.getRootLikelihoodsAndNames(Key.CChromatic, c, k);
				for(Integer score : thisResult.keySet()) {
					for(String s : thisResult.get(score)) {
						
					}
				}
				for(int i = 0; i < 12; i++) {
					//TODO Chord.
				}
			}
			
			
			return result;
		}

		private Iterator<StaffDelta> staffDeltaIterator(final Iterator<Rational> rhythm) {
			return new Iterator<StaffDelta>() {
				@Override
				public boolean hasNext() {
					return rhythm.hasNext();
				}

				@Override
				public StaffDelta next() {
					Rational r = rhythm.next();
					return staffDeltaAt(r);
				}

				@Override
				public void remove() { }
				
			};
		}
		
		/**
		 * Return a StaffDelta iterator that goes forewards from the supplied point
		 * 
		 * @param start the point to iterate  from
		 * @return
		 */
		public Iterator<StaffDelta> staffIterator(final Rational start) {
			return staffDeltaIterator(getOverallRhythm().tailSet(start).iterator());
		}
		
		/**
		 * Return a StaffDelta iterator that goes backwards from the supplied point
		 * 
		 * @param startEndSayWhat the point to iterate backwards from
		 * @return
		 */
		public Iterator<StaffDelta> reverseStaffIterator(final Rational startEndSayWhat) {
			return staffDeltaIterator(getOverallRhythm().headSet(startEndSayWhat,true).descendingIterator());
		}

		/**
		 * Fill end the noteNameCache field for every Chord, PitchSet and Scale in the given interval using the Keys
		 * present, or C Major if no key is provided.
		 * 
		 * @param start
		 * @param end
		 */
		@Deprecated
		public void realizeEnharmonics(Rational start, Rational end) {
			Iterator<StaffDelta> backwards = reverseStaffIterator(end);
			
			// This is set when we should define the notes based on the key rather than working backwards.
			// This is set to true to start, and then again whenever we have a key change (so the last notes
			// in a modulation from one key to another are named according to the previous key and we can have,
			// for instance D# to Eb ties).
			boolean isFirst = true;
			
			// We declare these variables here because we're working backwards
			PitchSet ps2 = null;
			Chord c2 = null;
			
			// Iterate backwards from our end point
			while(backwards.hasNext()) {
				StaffDelta sd = backwards.next();
				
				// Terminate when we've done what was asked
				if(sd.LOCATION.compareTo(start) < 0)
					break;
				
				// Fill in information from the Key
				if(isFirst) {
					
					isFirst = false;
				// Fill in information based on preceding Chords and PitchSets (ps2, c2)
				} else {
					Chord c1 = sd.ESTABLISHED.CHORD;
					for(Voice.VoiceDelta vd : sd.VOICES) {
						
						if(vd.CHANGED.NOTES != null) {
							Enharmonics.fillEnharmonics(vd.CHANGED.NOTES, c1, ps2, c2);
							ps2 = vd.CHANGED.NOTES;
						}
					}
				}
			}
		}
		
		public void fillNoteNames(Rational start, Rational end) {
			//TODO use the Enharmonics class for this
		}
		
		public Voice newVoice() {
			Voice[] newVoices = new Voice[_voices.length+1];
			for(int i = 0; i < _voices.length; i++)
				newVoices[i] = _voices[i];
			Voice v = new Voice();
			newVoices[_voices.length] = v;
			_voices = newVoices;
			return v;
		}
	}
	
	public class ScoreDelta {
		public Rational LOCATION = null;
		public Rational BEATNUMBER = null;
		public TimeSignature TIME_CHANGE_AFTER =  null;
		public boolean IS_END_OF_MEASURE = false;
		public boolean PRECEDES_FINE = false;
		
		/**
		 * A simple class for storing pointers to things in a Score.
		 * @author Jon Latane
		 *
		 */
		public class ScoreDeltaStuff {
			public TimeSignature TS = null;
            @Override
            public String toString() {
                return "TS: " + String.valueOf(TS);
            }
		}
		public ScoreDeltaStuff ESTABLISHED = new ScoreDeltaStuff();
		public ScoreDeltaStuff CHANGED = new ScoreDeltaStuff();
		
		public Staff.StaffDelta[] STAVES;

        @Override
        public String toString() {
            StringBuffer result = new StringBuffer();
            result.append("{" + String.valueOf(LOCATION) + ", beat " + String.valueOf(BEATNUMBER) + " of measure}\n");
            result.append("CHANGED:");
            result.append(CHANGED);
            result.append("\n");
            result.append("ESTABLISHED:\n");
            result.append(ESTABLISHED);
            result.append("\n\n");
            result.append("STAVES:\n");
            result.append(Arrays.toString(STAVES));
            return result.toString();
        }
	}
	
	public ScoreDelta scoreDeltaAt(Rational r) {
		ScoreDelta result = new ScoreDelta();
		result.LOCATION = r;
		
		Rational nextInOverallRhythm = getOverallRhythm().higher(r);
		if(nextInOverallRhythm != null) {
			result.TIME_CHANGE_AFTER = _meter._data.get(nextInOverallRhythm);
            if(_meter.getBeatOf(nextInOverallRhythm) != null)
			    result.IS_END_OF_MEASURE = _meter.getBeatOf(nextInOverallRhythm).compareTo( r ) < 0;
		} else {
			result.PRECEDES_FINE = true;
		}
		
		result.BEATNUMBER = _meter.getBeatOf(r);
		
		result.ESTABLISHED.TS = _meter.getObjectAt(r);
		result.CHANGED.TS = _meter._data.get(r);
		
		result.STAVES = new Staff.StaffDelta[_staves.length];
		for(int i = 0; i < _staves.length; i++) {
			Staff s = _staves[i];
			result.STAVES[i] = s.staffDeltaAt(r);
		}
		
		return result;
	}
	
	private Iterator<ScoreDelta> scoreDeltaIterator(final Iterator<Rational> rhythm) {
		return new Iterator<ScoreDelta>() {
			//private boolean encounteredFine = false;
			@Override
			public boolean hasNext() {
				return rhythm.hasNext();
			}

			@Override
			public ScoreDelta next() {
					Rational r = rhythm.next();
					return scoreDeltaAt(r);
			}

			@Override
			public void remove() { 
				throw new UnsupportedOperationException();
			}
			
		};
	}
	
	/**
	 * Return a ScoreDelta iterator that goes forward from the supplied point
	 * 
	 * @param start the point to iterate  from
	 * @return
	 */
	public Iterator<ScoreDelta> scoreIterator(final Rational start) {
		return scoreDeltaIterator(getOverallRhythm().tailSet(start).iterator());
	}
	
	public Iterator<ScoreDelta> scoreIterator(final Rational start, boolean inclusive) {
		return scoreDeltaIterator(getOverallRhythm().tailSet(start, inclusive).iterator());
	}
	
	/**
	 * Return a ScoreDelta iterator that goes backwards from the supplied point
	 * 
	 * @param startEndSayWhat the point to iterate backwards from
	 * @return
	 */
	public Iterator<ScoreDelta> reverseScoreIterator(final Rational startEndSayWhat) {
		return reverseScoreIterator(startEndSayWhat, true);
	}
	
	/**
	 * Return a ScoreDelta iterator that goes backwards from the supplied point
	 * 
	 * @param startEndSayWhat the point to iterate backwards from
	 * @param inclusive whether to include that point
	 * @return
	 */
	public Iterator<ScoreDelta> reverseScoreIterator(final Rational startEndSayWhat, boolean inclusive) {
		Log.i(TAG,"Backiteration: "+getOverallRhythm().headSet(startEndSayWhat,inclusive).toString());
		return scoreDeltaIterator(getOverallRhythm().headSet(startEndSayWhat,inclusive).descendingIterator());
	}
	
	public Rational getBeatOf(Rational r) {
		return _meter.getBeatOf(r);
	}
	
	public NavigableSet<Rational> getOverallRhythm() {
		TreeSet<Rational> result = new TreeSet<Rational>();
		//result.add(getFine());
		result.addAll(_meter.getRhythm());
		
		// Add all downbeats from the Meter from the beginning to the Fine by working backwards through it.
		// This may be able to be commented out (because TIEDVALUES should end up adding all downbeats
		// if properly set by the preprocessor).
		Rational lastMeterDefinition = getFine();
		for(Map.Entry<Rational, TimeSignature> e : _meter._data.descendingMap().entrySet()) {
			Rational downBeat = e.getKey();
			Rational incrementAmount = new Rational(e.getValue().TOP, 1);
			while(downBeat.compareTo(lastMeterDefinition) < 0) {
				result.add(downBeat);
				downBeat = downBeat.plus(incrementAmount);
			}
			lastMeterDefinition = e.getKey();
		}
		
		for(Staff s : _staves) {
			result.addAll(s._scales.getRhythm());
			result.addAll(s._chords.getRhythm());
			result.addAll(s._keys.getRhythm());
			result.addAll(s._clefs.getRhythm());

			for(Staff.Voice v : s._voices) {
				result.addAll(v._notes.getRhythm());
				for(Map.Entry<Rational, PitchSet> e : v._notes._data.entrySet()) {
					result.add(e.getKey());
					if(e.getValue() != null && e.getValue().tying != null)
						for(Rational tiedNoteheadPosition : e.getValue().tying) {
							result.add(e.getKey().plus(tiedNoteheadPosition));
						}
				}
			}
		}
		return result;
	}
	
	/**
	 * Create a new Staff in this Score with its own harmonic information.
	 * For polytonal music like The Doors' "Alabama Song."  Otherwise this
	 * is not particularly useful.
	 * 
	 * @return the created Staff
	 */
	public Staff newIndependentStaff(Clef c) {
		Staff[] newStaves = new Staff[_staves.length+1];
		for(int i = 0; i < _staves.length; i++)
			newStaves[i] = _staves[i];
		Staff s = new Staff(c);
		newStaves[_staves.length] = s;
		_staves = newStaves;
		return s;
	}
	
	public Staff newIndependentStaff() {
		return newIndependentStaff(Clef.treble());
	}
	
	/**
	 * If this Score has no Staves, create a blank one.  Otherwise, create a new Staff
	 * with identical chords, keys and scales to the first one in the Score.
	 * 
	 * @return the created Staff
	 */
	public Staff newStaff() {
		return newStaff(Clef.treble());
	}
	
	public Staff newStaff(Clef c) {
		Staff result = newIndependentStaff(c);
		if(_staves.length > 1) {
			result._chords = _staves[0]._chords;
			result._keys = _staves[0]._keys;
			result._scales = _staves[0]._scales;
		}
		return result;
		
	}

    /**
     * Return the array of Staves present in the Score
     * @return
     */
	public Staff[] getStaves() {
		return _staves;
	}

    /**
     * Get the Staff at a given index
     * @param n
     * @return
     */
	public Staff getStaff(int n) {
		return _staves[n];
	}

    /**
     * Get the number of Staves in this Score
     * @return
     */
	public int getNumStaves() {
		return _staves.length;
	}

    /**
     * Remove the Staff at the given index
     * @param n
     * @return
     */
	public Staff removeStaff(int n) {
		Staff result = _staves[n];
		Staff[] newStaves = new Staff[_staves.length-1];
		int iNew = 0;
		for(int i = 0; i < _staves.length; i++) {
			if(i != n) {
				newStaves[iNew] = _staves[i];
				iNew++;
			}
		}
		return result;
	}

    /**
     * Swap two Staves
     * @param a
     * @param b
     */
	public void swapStaves(int a, int b) {
		Staff staffA = _staves[a];
		_staves[a] = _staves[b];
		_staves[b] = staffA;
	}
	
	public void stripEnharmonics() {
		for(Staff staff : getStaves()) {
			for(Chord c : staff._chords._data.values()) {
				c.noteNameCache = null;
			}
			for(Voice v : staff.getVoices()) {
				for(PitchSet ps : v._notes._data.values()) {
					ps.noteNameCache = null;
				}
			}
		}
	}
	
	public static Score twinkleTwinkle() {
		Score result = new Score(new TimeSignature(2, 4), 16);
		
		Staff s1 = result.newStaff();
		Staff s2 = result.newStaff();
		s2._clefs.setDefaultValue(Clef.bass());
		
		//s1._keys.put(new Rational(1,1), new Key(Key.CMajor));
		
		Staff.Voice melody = s1.newVoice();
		Staff.Voice harmony = s2.newVoice();
		
		melody._notes.put(new Rational(1,1), PitchSet.toPitchSet("C2"));
		melody._notes.put(new Rational(2,1), PitchSet.toPitchSet("C4"));
		melody._notes.put(new Rational(3,1), PitchSet.toPitchSet("G4"));
		melody._notes.put(new Rational(4,1), PitchSet.toPitchSet("G4"));
		melody._notes.put(new Rational(5,1), PitchSet.toPitchSet("A4"));
		melody._notes.put(new Rational(6,1), PitchSet.toPitchSet("A4"));
		melody._notes.put(new Rational(7,1), PitchSet.toPitchSet("G4"));
		melody._notes.put(new Rational(9,1), PitchSet.toPitchSet("F4"));
		melody._notes.put(new Rational(10,1), PitchSet.toPitchSet("F4"));
		melody._notes.put(new Rational(11,1), PitchSet.toPitchSet("E4"));
		melody._notes.put(new Rational(12,1), PitchSet.toPitchSet("E4"));
		melody._notes.put(new Rational(13,1), PitchSet.toPitchSet("D4"));
		melody._notes.put(new Rational(14,1), PitchSet.toPitchSet("D4"));
		melody._notes.put(new Rational(15,1), PitchSet.toPitchSet("C4"));
		
		melody._notes.put(new Rational(17,1), PitchSet.toPitchSet("G4"));
		melody._notes.put(new Rational(18,1), PitchSet.toPitchSet("G4"));
		melody._notes.put(new Rational(19,1), PitchSet.toPitchSet("F4"));
		melody._notes.put(new Rational(20,1), PitchSet.toPitchSet("F4"));
		melody._notes.put(new Rational(21,1), PitchSet.toPitchSet("E4"));
		melody._notes.put(new Rational(22,1), PitchSet.toPitchSet("E4"));
		melody._notes.put(new Rational(23,1), PitchSet.toPitchSet("D4"));
		melody._notes.put(new Rational(25,1), PitchSet.toPitchSet("G4"));
		melody._notes.put(new Rational(26,1), PitchSet.toPitchSet("G7"));
		melody._notes.put(new Rational(27,1), PitchSet.toPitchSet("F4"));
		melody._notes.put(new Rational(28,1), PitchSet.toPitchSet("F4"));
		melody._notes.put(new Rational(29,1), PitchSet.toPitchSet("E4"));
		melody._notes.put(new Rational(30,1), PitchSet.toPitchSet("E4"));
		melody._notes.put(new Rational(31,1), PitchSet.toPitchSet("D4"));
		
		
		harmony._notes.put(new Rational(1,1), PitchSet.toPitchSet(new String[]{"C3", "E3", "G3"}));
		harmony._notes.put(new Rational(5,1), PitchSet.toPitchSet(new String[]{"C3", "F3", "A3"}));
		harmony._notes.put(new Rational(7,1), PitchSet.toPitchSet(new String[]{"C3", "E3", "G3"}));
		harmony._notes.put(new Rational(9,1), PitchSet.toPitchSet(new String[]{"C3", "F3", "A3"}));
		harmony._notes.put(new Rational(11,1), PitchSet.toPitchSet(new String[]{"C3", "E3", "G3"}));
		harmony._notes.put(new Rational(13,1), PitchSet.toPitchSet(new String[]{"Ab2", "C3", "Gb3"}));
		harmony._notes.put(new Rational(14,1), PitchSet.toPitchSet(new String[]{"G2", "B2", "G3"}));
		harmony._notes.put(new Rational(15,1), PitchSet.toPitchSet(new String[]{"C3", "E3", "G3"}));
		
		return result;
	}
	
	public static void testTwinkleTwinkle() {
		testScore(twinkleTwinkle());
	}
	
	/**
	 * Fill in the noteNameCache field for every Chord and PitchSet within this Score
	 * 
	 * @param s
	 */
	public static void fillEnharmonics(Score s) {
		// Iterate backward through the Score to fill its enharmonics.
		PitchSet[][] ps2Named = new PitchSet[s._staves.length][];
		Chord[] c2Named = new Chord[s._staves.length];

		for(int i = 0; i < s._staves.length; i++) {
			ps2Named[i] = new PitchSet[s._staves[i]._voices.length];
			for(int j = 0; j < s._staves[i]._voices.length; j++) {
				ps2Named[i][j] = null;
			}
			c2Named[i] = null;
		}
		
		Iterator<ScoreDelta> itr = s.reverseScoreIterator(s.getFine(), false);
		while(itr.hasNext()) {
			ScoreDelta scd = itr.next();
			if(scd.LOCATION.equals(new Rational(13, 1)))
					Log.i(TAG,"hi debugger");
			for(int i = 0; i < scd.STAVES.length; i++) {
				StaffDelta sd = scd.STAVES[i];
				Key key = sd.ESTABLISHED.KEY;
				Chord chord = sd.ESTABLISHED.CHORD;
				if(c2Named[i] == null || c2Named[i].equals(Chord.NO_CHORD)) {
					Enharmonics.fillEnharmonics( chord, key );
					c2Named[i] = chord;
				}
				for(int j = 0; j < s._staves[i]._voices.length; j++) {
					PitchSet ps = sd.VOICES[j].ESTABLISHED.NOTES;
					if(ps2Named[i][j] == null) {
						//if(chord != null || !Chord.NO_CHORD.equals(chord))
							Enharmonics.fillEnharmonics(ps, chord, key);
						//else
						//	Enharmonics.fillEnharmonics(ps, key);
					} else {
						Enharmonics.fillEnharmonics(ps, chord, ps2Named[i][j], c2Named[i], key);
					}
					ps2Named[i][j] = ps;
				}
				
				c2Named[i] = chord;
				
				// If there's a key change, notes should be named from THAT key rather
				// than voice leadings as we work our way backwards.
				if( sd.CHANGED.KEY != null) {
					for(int j = 0; j < s._staves[i]._voices.length; j++) {
						ps2Named[i][j] = null;
					}
					c2Named[i] = null;
				} else {
					
				}
			}
		}
	}
	
	public static void resolveTies(Score s) {
		resolveTies(s, 1);
	}
	
	public static void resolveTies(Score s, int maxNumberOfDotsAllowed) {
		Iterator<ScoreDelta> itr = s.reverseScoreIterator(s.getFine(), false);
		
		//Array ordering: Rational[STAFFNUMBER][VOICENUMBER]
		Rational[][] lastChangedNoteLocs = new Rational[s.getNumStaves()][];
		for(int i = 0; i < s.getNumStaves(); i++) {
			lastChangedNoteLocs[i] = new Rational[s.getStaff(i).getNumVoices()];
			for(int j = 0; j < lastChangedNoteLocs[i].length; j++) {
				lastChangedNoteLocs[i][j] = s.getFine();
			}
		}
		while(itr.hasNext()) {
			ScoreDelta scd = itr.next();
			for(int i = 0; i < scd.STAVES.length; i++) {
				StaffDelta sd = scd.STAVES[i];
				for(int j = 0; j < s._staves[i]._voices.length; j++) {
					VoiceDelta vd = sd.VOICES[j];
					if(vd.CHANGED.NOTES != null) {
						// This is a list of all the places a note MUST
						// be divided, i.e. barlines etc
						LinkedList<Rational> locations = new LinkedList<Rational>();
						
						// Divide the note up by the number of bars it's in first.
						Rational currentLoc = scd.LOCATION;
						while(currentLoc.compareTo(lastChangedNoteLocs[i][j]) < 0) {
							locations.add(currentLoc);
							currentLoc = s._meter.nextDownBeat(currentLoc);
						}
						locations.add(lastChangedNoteLocs[i][j]);
						
						// Go through each measured section
						for(int l = 0; l < locations.size() - 1; l++) {
							Rational begin = locations.get(l);
							Rational end = locations.get(l + 1);
							Rational duration = end.minus(begin);
							int subdivisionsAdded = 0;
							
							//TODO
							
							l += subdivisionsAdded;
						}
						Rational duration = locations.getLast().minus(locations.getFirst());
						while(duration.compareTo(Rational.ZERO) > 0) {
							Rational headLength = Rational.ONE;
							if(duration.compareTo(Rational.ONE) > 0) {
								// Get the base head length (1, 2, 4, 8, ...)
								while(headLength.times(Rational.TWO).compareTo(duration) <= 0) {
									headLength = headLength.times(Rational.TWO);
								}
								// Add dots
								for(int d = 1; d <= maxNumberOfDotsAllowed; d++) {
									if(headLength.times(Rational.ONE_AND_HALF).compareTo(duration) <= 0) {
										headLength = headLength.times(Rational.ONE_AND_HALF);
									} else {
										break;
									}
								}
							} else if(duration.compareTo(Rational.ONE) < 0) {
								// Get the base head length (1, 1/2, 1/4, 1/8, ...)
								while(headLength.times(Rational.HALF).compareTo(duration) >= 0) {
									headLength = headLength.times(Rational.HALF);
								}
								// Add dots
								for(int d = 1; d <= maxNumberOfDotsAllowed; d++) {
									if(headLength.times(Rational.ONE_AND_HALF).compareTo(duration) <= 0) {
										headLength = headLength.times(Rational.ONE_AND_HALF);
									} else {
										break;
									}
								}
							}
							
							locations.addLast(locations.getLast().plus(headLength));
							duration = duration.minus(headLength);
						}
						Collections.sort(locations);
						vd.CHANGED.NOTES.tying = new Rational[locations.size()+1];
						Rational headLocation = vd.LOCATION;
						int idx = 0;
						for(Rational location : locations) {
							Rational noteheadDuration = location.minus(headLocation);
							vd.CHANGED.NOTES.tying[idx++] = noteheadDuration;
							headLocation = headLocation.plus(noteheadDuration);
						}
						vd.CHANGED.NOTES.tying[idx] = lastChangedNoteLocs[i][j].minus(headLocation);
						
						//TODO finish this
					}
					lastChangedNoteLocs[i][j] = scd.LOCATION;
				}
			}

		}
	}
	
	/**
	 * Run a gamut of tests to make sure the given Score is consistent.
	 * 
	 * @param s
	 */
	public static void testScore(Score s) {
		fillEnharmonics(s);
		resolveTies(s);
		
		// Iterate forward through the Score to read its contents
		Iterator<ScoreDelta> itr = s.scoreIterator(Rational.ZERO);
		while(itr.hasNext()) {
			ScoreDelta scd = itr.next();
			assert(scd.STAVES.length == s.getNumStaves());
			
			Rational r = scd.LOCATION;
			Rational beat = scd.BEATNUMBER;
			
			String[] staffRepr = new String[s.getNumStaves()];
			
			for(int i = 0; i < scd.STAVES.length; i++) {
				StaffDelta sd = scd.STAVES[i];
				assert(r.compareTo(sd.LOCATION) == 0);
				assert(sd.VOICES.length == s.getStaff(i).getNumVoices());
				
				if( sd.CHANGED.CHORD != null ) {
					assert(sd.CHANGED.CHORD.equals(Chord.NO_CHORD) || sd.CHANGED.CHORD.getRoot() != null);
					if( sd.CHANGED.CHORD.equals(Chord.NO_CHORD) ) {
						staffRepr[i] = "N.C.";
					} else {
						staffRepr[i] = "[" + sd.CHANGED.CHORD.getRoot() + "]" + sd.CHANGED.CHORD.getCharacteristic();
					}
				} else {
					staffRepr[i] = "_";
				}
				
				//Add the Voice contents to the staffRepr
				String[] voiceRepr = new String[sd.VOICES.length];
				for(int j = 0; j < sd.VOICES.length; j++) { //Staff.Voice.VoiceDelta vd : sd.VOICES ) {
					Staff.Voice.VoiceDelta vd = sd.VOICES[j];
					if(vd.CHANGED.NOTES != null) {
						voiceRepr[j] = " ";
						for(int n : vd.CHANGED.NOTES) {
							voiceRepr[j] += n + " ";
						}
						if(vd.CHANGED.NOTES.noteNameCache != null) {
							for(int nameInd = 0; nameInd < vd.CHANGED.NOTES.noteNameCache.length; nameInd++) {
								voiceRepr[j] += vd.CHANGED.NOTES.noteNameCache[nameInd] + " ";
							}
							
						}
						if(vd.CHANGED.NOTES.equals(PitchSet.REST)) {
							voiceRepr[j] += "- ";
						}
					} else {
						if(vd.ESTABLISHED.NOTES != null && !vd.ESTABLISHED.NOTES.equals(PitchSet.REST)) {
							voiceRepr[j] = "_";
						} else {
							voiceRepr[j] = "-";
						}
					}
				}
				for(String str : voiceRepr) {
					staffRepr[i] += "|" + str;
				}
				staffRepr[i] += "|";
			}
			
			// Our String representation of the "line of music" (i.e, a vertical line through
			// the Score as on a page) to be spat out
			String lineRepr = r.toMixedString() + ": ";
			
			// Staff and voice information
			for(int i = 0; i < staffRepr.length; i++ ) {
				lineRepr += "|| " + staffRepr[i] + " ";
			}
			lineRepr += "||";
			
			Log.i(TAG, lineRepr);
		}
	}
}
