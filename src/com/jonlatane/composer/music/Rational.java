package com.jonlatane.composer.music;
/*************************************************************************
 *  Compilation:  javac Rational.java
 *  Execution:    java Rational
 *
 *  Immutable ADT for Rational numbers. 
 * 
 *  Invariants
 *  -----------
 *   - gcd(num, den) = 1, i.e, the rational number is in reduced form
 *   - den >= 1, the denominator is always a positive integer
 *   - 0/1 is the unique representation of 0
 *
 *  We employ some tricks to stave of overflow, but if you
 *  need arbitrary precision rationals, use BigRational.java.
 *
 *************************************************************************/

public class Rational implements Comparable<Rational> {
    public static final Rational ZERO = new Rational(0, 1);
    
    public static final Rational HALF = new Rational(1, 2);
    public static final Rational FOURTH = new Rational(1,4);
    public static final Rational EIGHTH = new Rational(1,8);
    public static final Rational SIXTEENTH = new Rational(1,16);
    public static final Rational THIRTYSECOND = new Rational(1,32);
    public static final Rational SIXTYFOURTH = new Rational(1,64);
    public static final Rational ONE = new Rational(1, 1);
    public static final Rational ONE_AND_HALF = new Rational(3, 2);
    public static final Rational TWO = new Rational(2, 1);
	
    private int num;   // the numerator
    private int den;   // the denominator

    public Rational(int integer) {
        this(integer,1);
    }

    // create and initialize a new Rational object
    public Rational(int numerator, int denominator) {
		
        // deal with x/0
        //if (denominator == 0) {
        //   throw new RuntimeException("Denominator is zero");
        //}
		
        // reduce fraction
        int g = gcd(numerator, denominator);
        num = numerator   / g;
        den = denominator / g;
		
        // only needed for negative numbers
        if (den < 0) { den = -den; num = -num; }
    }
	
    // return the numerator and denominator of (this)
    public int numerator()   { return num; }
    public int denominator() { return den; }
	
    // return double precision representation of (this)
    public double toDouble() {
        return (double) num / den;
    }
	
    // return string representation of (this)
    public String toString() { 
        if (den == 1) return num + "";
        else          return num + "/" + den;
    }
    
    public String toMixedString() {
    	int wholePart;
    	double d = toDouble();
    	if( d >= 0) {
    		wholePart = (int)toDouble();
    	} else {
    		wholePart = (int)Math.ceil(toDouble());
    	}

    	Rational fracPart = minus(new Rational(wholePart, 1));
    	
    	// Construct the result String
    	String result = "";
    	if(wholePart != 0) {
    		result += wholePart;
    	}
    	if(fracPart.num != 0) {
    		if(wholePart != 0)
    			result += " ";
    		result += fracPart.toString();
    	}
    	
    	return result;
    }
	
    // return { -1, 0, +1 } if a < b, a = b, or a > b
    public int compareTo(Rational b) {
        Rational a = this;
        int lhs = a.num * b.den;
        int rhs = a.den * b.num;
        if (lhs < rhs) return -1;
        if (lhs > rhs) return +1;
        return 0;
    }
	
    // is this Rational object equal to y?
    public boolean equals(Object y) {
        if (y == null) return false;
        if (y.getClass() != this.getClass()) return false;
        Rational b = (Rational) y;
        return compareTo(b) == 0;
    }
	
    // hashCode consistent with equals() and compareTo()
    public int hashCode() {
        return this.toString().hashCode();
    }
	
	
    // create and return a new rational (r.num + s.num) / (r.den + s.den)
    public static Rational mediant(Rational r, Rational s) {
        return new Rational(r.num + s.num, r.den + s.den);
    }
	
    // return gcd(|m|, |n|)
    private static int gcd(int m, int n) {
        if (m < 0) m = -m;
        if (n < 0) n = -n;
        if (0 == n) return m;
        else return gcd(n, m % n);
    }
	
    // return lcm(|m|, |n|)
    private static int lcm(int m, int n) {
        if (m < 0) m = -m;
        if (n < 0) n = -n;
        return m * (n / gcd(m, n));    // parentheses important to avoid overflow
    }
	
    // return a * b, staving off overflow as much as possible by cross-cancellation
    public Rational times(Rational b) {
        Rational a = this;
		
        // reduce p1/q2 and p2/q1, then multiply, where a = p1/q1 and b = p2/q2
        Rational c = new Rational(a.num, b.den);
        Rational d = new Rational(b.num, a.den);
        return new Rational(c.num * d.num, c.den * d.den);
    }
	
	
    // return a + b, staving off overflow
    public Rational plus(Rational b) {
        Rational a = this;
		
        // special cases
        if (a.compareTo(ZERO) == 0) return b;
        if (b.compareTo(ZERO) == 0) return a;
		
        // Find gcd of numerators and denominators
        int f = gcd(a.num, b.num);
        int g = gcd(a.den, b.den);
		
        // add cross-product terms for numerator
        Rational s = new Rational((a.num / f) * (b.den / g) + (b.num / f) * (a.den / g),
                                  lcm(a.den, b.den));
		
        // multiply back in
        s.num *= f;
        return s;
    }
	
    // return -a
    public Rational negate() {
        return new Rational(-num, den);
    }
	
    // return a - b
    public Rational minus(Rational b) {
        Rational a = this;
        return a.plus(b.negate());
    }
	
	
    public Rational reciprocal() { return new Rational(den, num);  }
	
    // return a / b
    public Rational divides(Rational b) {
        Rational a = this;
        return a.times(b.reciprocal());
    }
	
	public static Rational nearest( double x ) {
		// Restrict ourselves to around 1/256
		return nearest(x, 0.0039);
	}
	
	public static Rational nearest( double x, double epsilon ) {
		if( x == 0.0 ) {
			return Rational.ZERO;
		}
		if ( Math.abs(x) > Integer.MAX_VALUE || Math.abs(x) < 1 / (double)Integer.MAX_VALUE ) {
			return new Rational(0,0);
		}
		int sgn = 1;
		if (x < 0.0) {
			sgn = -1;
			x = -x;
		}
		int intPart = (int)x;
		double z = x-intPart;
		if( z != 0 ) {
			z = 1.0/z;
			int a = (int)z;
			z = z-a;
			int prevNum = 0;
			int num = 1;
			int prevDen = 1;
			int den=a;
			int tmp;
			double approxAns = ((double)den*intPart+num) / den;
			while(Math.abs((x-approxAns)/x) >= epsilon) {
				z = 1.0/z;
				a = (int)z;
				z = z-a;
				if((double)a*num+prevNum>Integer.MAX_VALUE || (double)a*den+prevDen > Integer.MAX_VALUE) {
					break;
				}
				tmp = a*num+prevNum;
				prevNum=num;
				num=tmp;
				tmp=a*den+prevDen;
				prevDen=den;
				den=tmp;
				approxAns=((double)den*intPart+num)/den;
			}
			return new Rational(sgn* (den*intPart+num), den);
		} else {
			return new Rational(sgn*intPart,1);
		}
	}

    /**
     * For instance, 2 mod 3/2 is 1/2.
     * @param modulus a positive Rational number
     * @return
     */
    public Rational mod(Rational modulus) {
        Rational result = this;
        while(result.compareTo(modulus) >= 0) {
            result = result.minus(modulus);
        }
        while(result.compareTo(Rational.ZERO) < 0) {
            result = result.plus(modulus);
        }
        return result;
    }

    public static Rational get(int num) {
        return get(num, 1);
    }

    public static Rational get(int num, int den) {
        return new Rational(num, den);
    }

}
