// -------------------------------------------------------
// ATK Matrix
//
// Coded by Joseph Anderson 2011
//
// -------------------------------------------------------

//------------------------------------------------------------------------
// (Gerzon's) Diametric Decoder Theorem (DDT)
//------------------------------------------------------------------------
//
// Much of the code below is a transcoding of Aaron Heller's Octave
// code available at: http://www.ai.sri.com/ajh/ambisonics/
//
// Benjamin, et al., "Localization in Horizontal-Only Ambisonic Systems"
// Preprint from AES-121, 10/2006, San Francisco
//
// Heller's original functions are noted through comments in each
// functions help field.
//
// Transcoding to Python/Numpy for use in muse/ATK/SC3 by
// Joseph Anderson <josephlloydanderson@mac.com>
//
// aes_paper.m (expanded version of speaker_matrix.m) contains the
// following functions:
//
//   velocity_gain_matrix()**            : compute alpha, beta, and gamma
//   speaker_matrix()                    : compute alpha, beta, and gamma
//   decoder_gain_matrix()               : compute decoder matrix
//
// ----------------------------------------
// the following functions are not included in SC3
// as are included in muse/ATK and not immediately
// useful in SC3 implementations
//
//   rV()                                : compute the Makita direction and rV
//   rE()                                : compute rE (and direction?)
//
//   _virtual_mic()                      : virtual mic angle and directivity
//   decoder_matrix_to_virtual_mic()     : computes loudspeaker 'virtual mics' 
//
// ----------------------------------------
// the following functions are not included
// as they duplicate muse/ATK functionality
//
//   az2dir()                            : convert azimuth to directon cosines
//   degrees()                           : convert radians to degrees
//   radians()                           : convert degrees to radians
//   gain_to_db()
//
//   rectangular_speaker_arrays()        : example decodes
//   hexagonal_speaker_arrays()          : example decodes
//
//
//
// NOTE: speaker_matrix() and velocity_gain_matrix() are the same code.
//       It appears these two separate names are used (in error) in Heller's
//       code. The expanded version, aes_paper.m defines velocity_gain_matrix(),
//       but calls speaker_matrix().
//
//------------------------------------------------------------------------
//
//
//------------------------------------------------------------------------
// DDT and related decoder matrix gains
//
//   NOTE:   These are the functions that compute gains to generate
//           loudspeaker feeds, and are not the functions which return
//           decoded B-format. See decoders, below.
//
//
//   speaker_matrix                  Heller's DDT (helper function)
//   decoder_gain_matrix             Heller's DDT (returns decoder gains)
//   panto_reg_decoder_gain_matrix   pantophonic
//   peri_reg_decoder_gain_matrix    periphonic
//   quad_decoder_gain_matrix        quad
//
//------------------------------------------------------------------------

//FOA {
//	var <w, <x, <y, <z;
//	
//	*new {arg w, x, y, z;
//		^super.newCopyArgs(w, x, y, z);
//	}
//	
//	*ar {arg w, x, y, z;
//		^this.new(w, x, y, z);
//	}
//	
//	madd {arg mul = 1, add = 0;
//		^MulAdd.ar([w, x, y, z], mul, add);	
//	}
//	
//	sig {arg mul = 1, add = 0;
//		^[w, x, y, z] * mul + add;
//	}
//	
//	asUGenInput {
//		^[w, x, y, z];
//	}
//	
//	asAudioRateInput {
//		^[w, x, y, z];
//	}
//}


//   speaker_matrix                  Heller's DDT (helper function)
AtkSpeakerMatrix {
	var <positions, <k, m, n;

	*new { arg directions, k;
		var positions;
		
		switch (directions.rank,					// 2D or 3D?
			1, { positions = Matrix.with(			// 2D
					directions.collect({ arg item;
						Polar.new(1, item).asPoint.asArray
					})
				)
			},
			2, { positions = Matrix.with(			// 3D
					directions.collect({ arg item;
						Spherical.new(1, item.at(0), item.at(1)).asCartesian.asArray
					})
				)
			}
		);
		
		^super.newCopyArgs(positions, k).initDiametric;
	}

	*newPositions { arg positions, k;
		^super.newCopyArgs(positions, k).initDiametric;
	}

	initDiametric {
		
    		// n = number of speaker pairs
    		// m = number of dimensions,
    		//        2=horizontal, 3=periphonic 
		m = this.positions.cols;
		n = this.positions.rows;
	}
	
	dim { ^m }
	numSpeakers { ^n * 2 }
	
	matrix {
		var s, directions, pos, dir;
		
	    	// scatter matrix accumulator
	    	s = Matrix.newClear(m, m);

		// speaker directions matrix
        	// NOTE: this isn't the user supplied directions arg
	    	directions = Matrix.newClear(m, n);
	
		n.do({ arg i;

			// allow entry of positions as
	    		// transpose for convenience
	    		// e.g., speaker positions are now in columns
	    		// rather than rows, then
	        	// get the i'th speaker position
	        	// e.g., select the i'th column
        		pos = positions.flop.getCol(i);

        		// normalize to get direction cosines
        		dir = pos /  pos.squared.sum.sqrt;
        		
        		// form scatter matrix and accumulate
        		s = s + Matrix.with(dir * dir.flop);

        		// form matrix of speaker directions
        		directions.putCol(i, dir)

			});
			
		// return resulting matrix
	 	^sqrt(1/2) * n * k * ( s.inverse * directions);
	}
}


////   decoder_gain_matrix             Heller's DDT (returns decoder gains)
////   panto_reg_decoder_gain_matrix   pantophonic
////   peri_reg_decoder_gain_matrix    periphonic
////   quad_decoder_gain_matrix        quad
AtkDecoderMatrix {
	var <k, <kind, decoderMatrix;

	var <positions, positions2;			// diametric
	var sm, m, n;
	var numSpeakers, <orientation;		// pantophonic
	var theta;
	var g0, g1;
	var numSpeakerPairs, <elevation;		// periphonic
	var directions;
	var up, down;
	var <angle;						// quadraphonic
	var alpha, beta;
		

	*newDiametric { arg directions, k;
		^super.newCopyArgs(k, 'diametric').initDiametric(directions);
	}
	
	*newPantophonic { arg numSpeakers, orientation, k;
		^super.newCopyArgs(k, 'pantophonic').initPantophonic(numSpeakers, orientation);
	}
	
	*newPeriphonic { arg numSpeakerPairs, elevation, orientation, k;
		^super.newCopyArgs(k, 'periphonic').initPeriphonic(numSpeakerPairs, elevation,
			orientation);
	}
	
	*newQuadraphonic { arg angle, k;
		^super.newCopyArgs(k, 'quadraphonic').initQuadraphonic(angle);
	}
	
	initDiametric { arg directions;

		switch (directions.rank,					// 2D or 3D?
			1, { positions = Matrix.with(			// 2D
					directions.collect({ arg item;
						Polar.new(1, item).asPoint.asArray
					})
				)
			},
			2, { positions = Matrix.with(			// 3D
					directions.collect({ arg item;
						Spherical.new(1, item.at(0), item.at(1)).asCartesian.asArray
					})
				)
			}
		);

		// list all of the speakers
		// i.e., expand to actual pairs
		positions2 = positions ++ (positions.neg);

	    	// get velocity gains
	    	// NOTE: this comment from Heller seems to be slightly
	    	//       misleading, in that the gains returned will be
	    	//       scaled by k, which may not request a velocity
	    	//       gain. I.e., k = 1 isn't necessarily true, as it
	    	//       is assigned as an argument to this function.
	    	sm = AtkSpeakerMatrix.newPositions(positions2, k).matrix;
	    
	    	// n = number of speakers
	    	// m = number of dimensions,
		//        2=horizontal, 3=periphonic 
		m = sm.rows;
		n = sm.cols;
	}
	
	initPantophonic { arg numSpeaks, orient;
		numSpeakers = numSpeaks;
		orientation = orient;
	    	g0 = 1.0;
	    	g1 = 2.sqrt;

		// define function to return theta from speaker number
		theta = { arg speaker;
			switch (orientation,
				'flat',	{ ((1.0 + (2.0 * speaker))/numSpeakers) * pi },
				'point',	{ ((2.0 * speaker)/numSpeakers) * pi }
			)
		};
	}
	
	initPeriphonic { arg numSpeakPairs, elev, orient;
		numSpeakerPairs = numSpeakPairs;
		elevation = elev;
		orientation = orient;
		numSpeakers = numSpeakPairs * 2;

		// generate speaker pair positions
		// start with polar positions. . .
		theta = [];
		numSpeakerPairs.do({arg i;
			theta = theta ++ [2 * pi * i / numSpeakerPairs]}
		);
		if ( orientation == 'flat',
			{ theta = theta + (pi / numSpeakerPairs) });       // 'flat' case

		// collect directions [ [theta, phi], ... ]
		directions = [
			theta,
			Array.newClear(numSpeakerPairs).fill(elevation)
		].flop;
	}
	
	initQuadraphonic { arg ang;
		angle = ang;
	}
	
	dim {
		switch (kind,
			'diametric',		{ ^AtkSpeakerMatrix.newPositions(positions, k).dim },
			'pantophonic',	{ ^2 },
			'periphonic',		{ ^3 },
			'quadraphonic',	{ ^2 }
		) 
	}

	numSpeakers {
		switch (kind,
			'diametric',		{ ^AtkSpeakerMatrix.newPositions(positions, k).numSpeakers },
			'pantophonic',	{ ^numSpeakers },
			'periphonic',		{ ^numSpeakers },
			'quadraphonic',	{ ^4 }
		) 
	}
	
	dirSpeakers {
		switch (kind,
			'diametric', {
				switch (AtkSpeakerMatrix.newPositions(positions, k).dim, // 2D or 3D?
					2, { ^positions2.asArray.collect({ arg item; // 2D
							item.asPoint.asPolar.angle
						})
					},
					3, { ^positions2.asArray.collect({ arg item; // 3D
							item.asCartesian.asSpherical.angles
						})
					}
				);
			},

			'pantophonic', {
				^(numSpeakers.collect({ arg i;
					theta.value(i)
				}) + pi).mod(2pi) - pi;
			},

			'periphonic', {
				up = (directions + pi).mod(2pi) - pi;
				
				down = up.collect({ arg angles;
					Spherical.new(1, angles.at(0), angles.at(1)).neg.angles
				});
				
				down = if ( (orientation == 'flat') && (numSpeakerPairs.mod(2) == 1),
					{ down.rotate((numSpeakerPairs/2 + 1).asInteger) }, // odd, 'flat'
					{ down.rotate((numSpeakerPairs/2).asInteger) }     // 'flat' case, default
				);
				
				^up ++ down;
			},
			'quadraphonic', { ^[ angle, pi - angle, (pi - angle).neg, angle.neg] }
		) 
	}

	matrix {
		switch (kind,
			'diametric', {

				// build decoder matrix 
				// rows are W, X, and Y gains
				// NOTE: this matrix construction can be simplified
				//       with a concatenation (hstack) of a column
				//       of ones and sm
			    	decoderMatrix = Matrix.newClear(m + 1, n) + 1;
			    	n.do({ arg i;
					m.do({ arg j;
						decoderMatrix.put(j + 1, i, sm.at(j, i))
						});
				    });
		
				// return resulting matrix
				// ALSO: the below code calls for the complex conjugate
				//       of decoder_matrix. As we are expecting real vaules,
				//       we may regard this call as redundant.
				// res = sqrt(2)/n * decoder_matrix.conj().transpose()
				^2.sqrt/n * decoderMatrix.flop;
			},

			'pantophonic', {

				// calculate decoding matrix
				decoderMatrix = Matrix.newClear(numSpeakers, 3); // start w/ empty matrix
			
				numSpeakers.do({ arg i;
					decoderMatrix.putRow(i, [
						g0,
			              k * g1 * theta.value(i).cos,
			              k * g1 * theta.value(i).sin
					])
					});
				
				// return resulting matrix
				^2.sqrt/numSpeakers * decoderMatrix
			},

			'periphonic',	{
		
				// compute the decoder
				decoderMatrix = AtkDecoderMatrix.newDiametric(directions, k).matrix;

				// reorder the lower polygon
				up = decoderMatrix[..(numSpeakerPairs-1)];
				down = decoderMatrix[(numSpeakerPairs)..];
		
				down = if ( (orientation == 'flat') && (numSpeakerPairs.mod(2) == 1),
					{ down.rotate((numSpeakerPairs/2 + 1).asInteger) }, // odd, 'flat'
					{ down.rotate((numSpeakerPairs/2).asInteger) }     // 'flat' case, default
				);
				
				decoderMatrix = up ++ down;
		
				^decoderMatrix
			},

			'quadraphonic', {

				// calculate alpha, beta (scaled by k)
				alpha   = k / (2.sqrt * angle.cos);
				beta    = k / (2.sqrt * angle. sin);
		
		
				// fill decoding matrix
			    decoderMatrix = Matrix.with([
			    		[1, alpha, beta],
			        	[1, alpha.neg, beta],
			        	[1, alpha.neg, beta.neg],
			        	[1, alpha, beta.neg]
			    ]);
			    
			    ^2.sqrt/4 * decoderMatrix
			}
		) 
	}
}