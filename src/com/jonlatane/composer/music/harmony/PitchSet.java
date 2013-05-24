package com.jonlatane.composer.music.harmony;

import java.util.*;

import com.jonlatane.composer.music.Rational;

/**
* A PitchSet represents a set of pitches.  In this model, middle C (C4) is 0.
* 
* Of important note are the fields NOTENAMES and DURATION.  These provide a caching layer - while a PitchSet
* does not manage this information itself (as context is needed to fill in these fields) the rendering layer
* may store it in these fields and invalidate them by setting them to null.
*/
public class PitchSet extends TreeSet<Integer> {
	private static final long serialVersionUID = -3127526528166358783L;

	/**
	 * NOTENAMES is a field to be filled in by the rendering layer.  It determines whether a C chord
	 * is rendered as CEG or B#FbG, essentially.  NOTENAMES should be filled to match the notes of
	 * the PitchSet/Chord/Scale in ascending order.  This means, in Chords, the note names are essentially
	 * listed from C on up for each note in the Chord, regardless of its root.
	 */
	public String[] NOTENAMES = null;
	
	/**
	 * This is used for deciding whether to render something of duration 1 and 1/4 as a quarter
	 * tied to a sixteenth or a sixteenth tied to a quarter.  It can be determined at runtime
	 * in the rendering layer and cached here in the model.  The rendering layer uses the time
	 * the PitchSet had its onset/attack/initiation and this to render the noteheads where they
	 * need to go.  For no ties, this is just an array of size 1.
	 */
	public Rational[] TIEDVALUES = null;
	public Rational runtimeDuration() {
		Rational result = Rational.ZERO;
		for(Rational r : TIEDVALUES)
			result = result.plus(r);
		return result;
	}
	
	public PitchSet() {
		super();
	}
	
	public PitchSet(int i) {
		super();
		add(i);
	}
	
	public PitchSet(Collection<Integer> c) {
		super(c);
	}
	
	/**
	 * It is imperative to use this and NOT a null pointer to represent a rest.
	 * Null pointers indicate the end of the score.
	 */
	public static final PitchSet REST = new PitchSet();
	
	/**
	 * Convert a note name ("C4", "D") to a PitchSet containing that note.  If no octave is specified,
	 * it is assumed to be 4.
	 * 
	 * @param note
	 * @return
	 */
	public static PitchSet toPitchSet(String note) {
		PitchSet result = new PitchSet();
		result.add(Enharmonics.noteNameToInt(note));
		return result;
	}

	/**
	 * Convert an array of note names (C3, G3, E4, B4) to a PitchSet
	 * 
	 * @param notes an array of note names.  If no octave is specified they are assumed to be in the 4th octave
	 * @return
	 */
	public static PitchSet toPitchSet(String[] notes) {
		PitchSet result = new PitchSet();
		for(String s : notes) {
			result.add(Enharmonics.noteNameToInt(s));
		}
		return result;
	}
	
	@Override
	public int hashCode() {
		int result = 2;
		for(Integer i : this ) {
			result = result ^ i;
		}
		return result;
	}
}
