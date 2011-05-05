package si.ijs.slner;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.zip.ZipException;

import javax.xml.stream.XMLStreamException;

import si.ijs.slner.tei.Doc;
import si.ijs.slner.tei.DocReaders;
import bsh.EvalError;
import cc.mallet.fst.CRF;
import cc.mallet.fst.CRFTrainerByL1LabelLikelihood;
import cc.mallet.fst.CRFTrainerByLabelLikelihood;
import cc.mallet.fst.CRFTrainerByStochasticGradient;
import cc.mallet.fst.CRFTrainerByValueGradients;
import cc.mallet.fst.MultiSegmentationEvaluator;
import cc.mallet.fst.Transducer;
import cc.mallet.fst.ViterbiWriter;
import cc.mallet.fst.semi_supervised.CRFTrainerByEntropyRegularization;
import cc.mallet.pipe.Noop;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.PrintTokenSequenceFeatures;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureVectorSequence;
import cc.mallet.pipe.iterator.LineGroupIterator;
import cc.mallet.pipe.tsf.FeaturesInWindow;
import cc.mallet.pipe.tsf.OffsetConjunctions;
import cc.mallet.pipe.tsf.RegexMatches;
import cc.mallet.pipe.tsf.TokenText;
import cc.mallet.pipe.tsf.TokenTextCharNGrams;
import cc.mallet.share.mccallum.ner.TUI;
import cc.mallet.types.Alphabet;
import cc.mallet.types.InstanceList;
import cc.mallet.util.CommandOption;


public class SloveneNER {


	private static String CAPS = "[\\p{Lu}]";
	private static String LOW = "[\\p{Ll}]";
	private static String CAPSNUM = "[\\p{Lu}\\p{Nd}]";
	private static String ALPHA = "[\\p{Lu}\\p{Ll}]";
	private static String ALPHANUM = "[\\p{Lu}\\p{Ll}\\p{Nd}]";
	private static String PUNT = "[,\\.;:?!()]";
	private static String QUOTE = "[\"`']";
	
	static CommandOption.String inOption = new CommandOption.String
	(TUI.class, "in", "e.g. corpus.xml", true, "", "Input file", null); 
	
	//(owner, name, argName, argRequired, defaultValue, shortdoc, longdoc)
	
	static CommandOption.String offsetsOption = new CommandOption.String
	(TUI.class, "offsets", "e.g. [[0,0],[1]]", true, "[[-2],[-1],[1],[2]]", 
	 "Offset conjunctions", null);
	
	static CommandOption.String capOffsetsOption = new CommandOption.String
	(TUI.class, "cap-offsets", "e.g. [[0,0],[0,1]]", true, "", 
	 "Offset conjunctions applied to features that are [A-Z]*", null);
	
	static CommandOption.Integer wordWindowFeatureOption = new CommandOption.Integer
	(TUI.class, "word-window-size", "INTEGER", true, 5,
	 "Size of window of words as features: 0=none, 10, 20...", null);

	static CommandOption.Boolean charNGramsOption = new CommandOption.Boolean
	(TUI.class, "char-ngrams", "true|false", true, false,
	 "", null);
	
	static CommandOption.Boolean useFeatureInductionOption = new CommandOption.Boolean
	(TUI.class, "use-feature-induction", "true|false", true, true,
	 "Not use or use feature induction", null);

	static CommandOption.Boolean clusterFeatureInductionOption = new CommandOption.Boolean
	(TUI.class, "cluster-feature-induction", "true|false", true, false,
	 "Cluster in feature induction", null);
	
	static final CommandOption.List commandOptions =
		new CommandOption.List (
			"Training, testing and running a Chinese word segmenter.",
			new CommandOption[] {
				/*gaussianVarianceOption,
				hyperbolicSlopeOption,
				hyperbolicSharpnessOption,
				randomSeedOption,
				labelGramOption,*/
				inOption,
				wordWindowFeatureOption,
				//useHyperbolicPriorOption,
				useFeatureInductionOption,
				clusterFeatureInductionOption,
				/*useFirstMentionFeatureOption,
				useDocHeaderFeatureOption,
				includeConllLexiconsOption,*/
				offsetsOption,
				capOffsetsOption//,
				/*viterbiFilePrefixOption,
				useTestbOption,*/
			});

	/**
	 * @param args
	 * @throws EvalError 
	 * @throws XMLStreamException 
	 * @throws IOException 
	 * @throws ZipException 
	 */
	public static void main(String[] args) throws EvalError, ZipException, IOException, XMLStreamException {
		// TODO Auto-generated method stub
		commandOptions.process (args);
		//String outFile = args[1];

		test( inOption.value());
		
	}



	public static void test(String inFile) throws EvalError,
			ZipException, IOException, XMLStreamException {
		String offsetsString = offsetsOption.value.replace('[','{').replace(']','}');
		int[][] offsets = (int[][]) CommandOption.getInterpreter().eval ("new int[][] "+offsetsString);

		String capOffsetsString = capOffsetsOption.value.replace('[','{').replace(']','}');
		int[][] capOffsets = null;
		if (capOffsetsString.length() > 0)
			capOffsets = (int[][]) CommandOption.getInterpreter().eval ("new int[][] "+capOffsetsString);

		
		Pipe p = new SerialPipes(new Pipe[] {
				new SentencePipe(true),
				new RegexMatches ("INITCAP", Pattern.compile (CAPS+".*")),
				new RegexMatches ("CAPITALIZED", Pattern.compile (CAPS+LOW+"*")),
//				new RegexMatches ("ALLCAPS", Pattern.compile (CAPS+"+")),
//				new RegexMatches ("MIXEDCAPS", Pattern.compile ("[A-Z][a-z]+[A-Z][A-Za-z]*")),
//				new RegexMatches ("CONTAINSDIGITS", Pattern.compile (".*[0-9].*")),
//				new RegexMatches ("ALLDIGITS", Pattern.compile ("[0-9]+")),
//				new RegexMatches ("NUMERICAL", Pattern.compile ("[-0-9]+[\\.,]+[0-9\\.,]+")),
//				new RegexMatches ("ALPHNUMERIC", Pattern.compile ("[A-Za-z0-9]+")),
//				new RegexMatches ("ROMAN", Pattern.compile ("[ivxdlcm]+|[IVXDLCM]+")),
//				new RegexMatches ("MULTIDOTS", Pattern.compile ("\\.\\.+")),
//				new RegexMatches ("ENDSINDOT", Pattern.compile ("[^\\.]+.*\\.")),
				new RegexMatches ("CONTAINSDASH", Pattern.compile (ALPHANUM+"+-"+ALPHANUM+"*")),
				new RegexMatches ("ACRO", Pattern.compile ("[A-Z][A-Z\\.]*\\.[A-Z\\.]*")),
				new RegexMatches ("LONELYINITIAL", Pattern.compile (CAPS+"\\.")),
//				new RegexMatches ("SINGLECHAR", Pattern.compile (ALPHA)),
//				new RegexMatches ("CAPLETTER", Pattern.compile ("[A-Z]")),
//				new RegexMatches ("PUNC", Pattern.compile (PUNT)),
//				new RegexMatches ("QUOTE", Pattern.compile (QUOTE)),
//				new RegexMatches ("LOWER", Pattern.compile (LOW+"+")),
				new RegexMatches ("MIXEDCAPS", Pattern.compile ("[A-Z]+[a-z]+[A-Z]+[a-z]*")),
				//new TokenText ("W="),
				new OffsetConjunctions (offsets),
				(capOffsets != null ? (Pipe) new OffsetConjunctions (capOffsets) : (Pipe) new Noop ()),
						
				//(wordWindowFeatureOption.value > 0
				//(Pipe) new FeaturesInWindow ("WINDOW=", -wordWindowFeatureOption.value, wordWindowFeatureOption.value,	Pattern.compile ("WORD=.*"), true),
				// : (Pipe) new Noop()),
				//(charNGramsOption.value
				// ? (Pipe) new TokenTextCharNGrams ("CHARNGRAM=", new int[] {2,3,4})
				// : (Pipe) new Noop()),

				//new PrintTokenSequenceFeatures(),
				new TokenSequence2FeatureVectorSequence (true, true)
		});
		
		Doc d = DocReaders.openFile(new File(inFile)).get(0);
		

		
		InstanceList allData = new InstanceList(p);
		allData.addThruPipe (new SentenceIterator (d));
	
		System.out.println ("Read "+allData.size()+" instances");
		
		InstanceList[] splits = allData.split(new Random(), new double[]{.7,.3});
		InstanceList trainingData = splits[0];
		InstanceList testingData = splits[1];
	
		//InstanceList unlabeled = new InstanceList(p);
		//unlabeled.addThruPipe(new SentenceIterator(DocReaders.openFile(new File("/home/tadej/workspace/slner/jos100k-train.xml.zip")).get(0)));
		

		// Print out all the target names
		Alphabet targets = p.getTargetAlphabet();
		System.out.print ("State labels:");
		for (int i = 0; i < targets.size(); i++)
			System.out.print (" " + targets.lookupObject(i));
		System.out.println ("");

		// Print out some feature information
		System.out.println ("Number of features = "+p.getDataAlphabet().size());
		
		CRF crf = new CRF(p, null);
		crf.addStatesForLabelsConnectedAsIn(trainingData);
		
		//CRFTrainerByStochasticGradient crft = new CRFTrainerByStochasticGradient(crf, 2);
		//CRFTrainerByEntropyRegularization crft = new CRFTrainerByEntropyRegularization(crf);
		CRFTrainerByLabelLikelihood crft = new CRFTrainerByLabelLikelihood(crf);
		//crft.setUseSomeUnsupportedTrick(true);
		crft.setUseSparseWeights(true);
		//crft.setUseHyperbolicPrior(true);
		//CRFTrainerByL1LabelLikelihood crft = new CRFTrainerByL1LabelLikelihood(crf);
		//CRFTrainerByValueGradients crft = new CRFTrainerByValueGradients(`, optimizableByValueGradientObjects)

		/*for (int i = 0; i < crf.numStates(); i++) {
			Transducer.State s = crf.getState (i);
			if (s.getName().charAt(0) == 'I')
				s.setInitialWeight (Double.POSITIVE_INFINITY);
		}*/
		
		System.out.println("Training on "+trainingData.size()+" training instances, "+
				 testingData.size()+" testing instances...");

		MultiSegmentationEvaluator eval =
			new MultiSegmentationEvaluator (new InstanceList[] {trainingData, testingData},
					new String[] {"Training", "Testing"},
					new String[] {"PERSON", "LOCATION", "ORG", "PROD"},
					new String[] {"PERSON", "LOCATION", "ORG", "PROD"});
		ViterbiWriter vw = new ViterbiWriter ("out",
				new InstanceList[] {trainingData, testingData}, new String[] {"Training", "Testing"});
			
		if (useFeatureInductionOption.value) {
			if (clusterFeatureInductionOption.value)
				crft.trainWithFeatureInduction (trainingData, null, testingData,
																			 eval, 99999,
																			 10, 99, 200, 0.5, true,
																			 new double[] {.1, .2, .5, .7});
			else
				crft.trainWithFeatureInduction (trainingData, null, testingData,
																			 eval, 99999,
																			 10, 99, 1000, 0.5, false,
																			 new double[] {.1, .2, .5, .7});
			
			eval.evaluate(crft);
		}
		else {
			/*double[] trainingProportions = new double[] {.1, .2, .5, .7};
			for (int i = 0; i < trainingProportions.length; i++) {
				crft.train(trainingData, 5, new double[] {trainingProportions[i]});
				eval.evaluate(crft);
				vw.evaluate(crft);
			}*/
			while (crft.train(trainingData/*, unlabeled*/, 10)) {
				eval.evaluate(crft);
				//vw.evaluate(crft);
			}
			eval.evaluate(crft);
			//vw.evaluate(crft);
		}
		
	}

}