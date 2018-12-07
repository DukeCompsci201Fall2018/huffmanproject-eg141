
import java.util.*;
/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {
	
	//one 8-bit chunk represents a character in a leaf node

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);
		
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
	}
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		
		//translation goes through in and turns to out, using codings, in 8-bit chunks (for one character)
		//how to deal with PSEUDO_EOF
		int x = in.readBits(BITS_PER_WORD);
		while(x != -1) {
			String code = codings[x];
			out.writeBits(code.length(), Integer.parseInt(code, 2));
			in.readBits(BITS_PER_WORD);

			}
		
		out.writeBits(codings[PSEUDO_EOF].length(), Integer.parseInt(codings[PSEUDO_EOF], 2));
		}

		


	private void writeHeader(HuffNode root, BitOutputStream out) {
		
		if(root == null) {
			return;
		}
		
		if(root.myWeight == 0) {
			 out.writeBits(1,0);
			 writeHeader(root.myLeft, out);
			 writeHeader(root.myRight, out);
		}
	
		else {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
		}
		
		
	}

	private String[] makeCodingsFromTree(HuffNode root) {
		String[]encodings = new String[ALPH_SIZE + 1];
		codingHelper(root,"", encodings);
		
		return encodings;
	}

	private void codingHelper(HuffNode root, String path, String[] encodings) {
		
		if(root == null) {
			return;
		}
		
		if(root.myWeight==1) {
			encodings[root.myValue] = path;
			return;
		}
		else {
			codingHelper(root.myLeft, path + "0", encodings);
			codingHelper(root.myRight,path + "1", encodings);	
		}
		
	}

	private HuffNode makeTreeFromCounts(int[] counts) { //make sure PSEUDO_EOF in tree***x
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for(int x = 0; x < counts.length; x++) {
			if(counts[x] > 0) {
				pq.add(new HuffNode(x, counts[x], null, null));
			}
			//counts used as order in priority queue, smallest counts huff nodes removed first
		}
		while(pq.size() > 1) { 
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(0, left.myWeight+right.myWeight, left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	//will=goat
	private int[] readForCounts(BitInputStream in) {
		int[] freq = new int[ALPH_SIZE + 1];
		int first = in.readBits(BITS_PER_WORD);
		while(first != -1) {
			freq[first] += 1;
			first = in.readBits(BITS_PER_WORD); 
		}
		freq[PSEUDO_EOF] = 1;
		
		return freq;
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		
		int bits = in.readBits(BITS_PER_INT);
		if(bits!=HUFF_TREE) { 
			throw new HuffException("illegal header starts with" + bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		
		
		out.close();
	}

	// word has 8 bits 
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		// stop reading when reach leaf that is PSEUDO _ EOF
		HuffNode current = root;
		
		while(true) {
			if(current == null) {
				return;
			}
			int bits = in.readBits(1);
			if(bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else {
				//re
				if(bits == 0) current = current.myLeft;
				else current = current.myRight;
				
				if(current!= null && current.myWeight == 1) { // checks if current is a leaf node
					if(current.myValue == PSEUDO_EOF) { 
						break;	
					}
					else { // then write value for leaf 
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root; // start back after leaf
					}
				}
			}
		}
		
	}

	private HuffNode readTreeHeader(BitInputStream in) {
		//taking formerly compressed stream of bits to create tree that represents bits
		 
		int bit = in.readBits(1); // if can't read single bit will return -1
		if(bit == -1) {
			throw new HuffException("illegal input");
		}
		//read self then left then right for pre-order traversal
		if(bit == 0) {// haven't hit a leaf yet need to make recursive call 
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0, left, right);	
		}
		else {
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value, 0, null, null);
		}
	}
}