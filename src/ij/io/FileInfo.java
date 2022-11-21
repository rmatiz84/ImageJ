package ij.io;
import java.io.InputStream;

import ij.IJ;
import ij.VirtualStack;

/** This class consists of public fields that describe an image file. */
public class FileInfo implements Cloneable {
	
	/* File format (TIFF, GIF_OR_JPG, BMP, etc.). Used by the File/Revert command */
	public int fileFormat;
	
	/* File type (GRAY8, GRAY_16_UNSIGNED, RGB, etc.) */
	public int fileType;	
	public String fileName;
	public String directory;
	public String url;
    public int width;
    public int height;
    public int offset=0;  // Use getOffset() to read
    public int nImages;
    public int gapBetweenImages;   // Use getGap() to read
    public boolean whiteIsZero;
    public boolean intelByteOrder;
	public int compression;
    public int[] stripOffsets;  
    public int[] stripLengths;
    public int rowsPerStrip;
	public int lutSize;
	public byte[] reds;
	public byte[] greens;
	public byte[] blues;
	public Object pixels;	
	public String debugInfo;
	public String[] sliceLabels;
	public String info;
	public InputStream inputStream;
	public VirtualStack virtualStack;
	public int sliceNumber; // used by FileInfoVirtualStack
	
	public double pixelWidth=1.0;
	public double pixelHeight=1.0;
	public double pixelDepth=1.0;
	public String unit;
	public int calibrationFunction;
	public double[] coefficients;
	public String valueUnit;
	public double frameInterval;
	public String description;
	// Use <i>longOffset</i> instead of <i>offset</i> when offset>2147483647.
	public long longOffset;  // Use getOffset() to read
	// Use <i>longGap</i> instead of <i>gapBetweenImages</i> when gap>2147483647.
	public long longGap;  // Use getGap() to read
	// Extra metadata to be stored in the TIFF header
	public int[] metaDataTypes; // must be < 0xffffff
	public byte[][] metaData;
	public double[] displayRanges;
	public byte[][] channelLuts;
	public byte[] plot;			// serialized plot
	public byte[] roi;			// serialized roi
	public byte[][] overlay;	// serialized overlay objects
	public int samplesPerPixel;
	public String openNextDir, openNextName;
	public String[] properties; // {key,value,key,value,...}
	public boolean imageSaved;
    
	/** Creates a FileInfo object with all of its fields set to their default value. */
     public FileInfo() {
    	// assign default values
    	fileFormat = Constants.UNKNOWN;
    	fileType = Constants.GRAY8;
    	fileName = "Untitled";
    	directory = "";
    	url = "";
	    nImages = 1;
		compression = Constants.COMPRESSION_NONE;
		samplesPerPixel = 1;
    }
    
     /** Returns the file path. */
	public String getFilePath() {
		String dir = directory;
		if (dir==null)
			dir = "";
		dir = IJ.addSeparator(dir);
		return dir + fileName;
	}

   /** Returns the offset as a long. */
    public final long getOffset() {
    	return longOffset>0L?longOffset:((long)offset)&0xffffffffL;
    }
    
    /** Returns the gap between images as a long. */
    public final long getGap() {
    	return longGap>0L?longGap:((long)gapBetweenImages)&0xffffffffL;
    }

	/** Returns the number of bytes used per pixel. */
	public int getBytesPerPixel() {
		switch (fileType) {
			case Constants.GRAY8: case Constants.COLOR8: case Constants.BITMAP: return 1;
			case Constants.GRAY16_SIGNED: case Constants.GRAY16_UNSIGNED: case Constants.GRAY12_UNSIGNED: return 2;
			case Constants.GRAY32_INT: case Constants.GRAY32_UNSIGNED: case Constants.GRAY32_FLOAT: case Constants.ARGB: case Constants.GRAY24_UNSIGNED: case Constants.BARG: case Constants.ABGR: case Constants.CMYK: return 4;
			case Constants.RGB: case Constants.RGB_PLANAR: case Constants.BGR: return 3;
			case Constants.RGB48: case Constants.RGB48_PLANAR: return 6;
			case Constants.GRAY64_FLOAT : return 8;
			default: return 0;
		}
	}

    public String toString() {
    	return
    		"name=" + fileName
			+ ", dir=" + directory
			+ ", width=" + width
			+ ", height=" + height
			+ ", nImages=" + nImages
			+ ", offset=" + getOffset()
			+ ", gap=" + getGap()
			+ ", type=" + getType()
			+ ", byteOrder=" + (intelByteOrder?"little":"big")
			+ ", format=" + fileFormat
			+ ", url=" + url
			+ ", whiteIsZero=" + (whiteIsZero?"t":"f")
			+ ", lutSize=" + lutSize
			+ ", comp=" + compression
			+ ", ranges=" + (displayRanges!=null?""+displayRanges.length/2:"null")
			+ ", samples=" + samplesPerPixel;
    }
    
    /** Returns JavaScript code that can be used to recreate this FileInfo. */
    public String getCode() {
    	String code = "fi = new FileInfo();\n";
    	String type = null;
    	if (fileType==Constants.GRAY8)
    		type = "GRAY8";
    	else if (fileType==Constants.GRAY16_UNSIGNED)
    		type = "GRAY16_UNSIGNED";
    	else if (fileType==Constants.GRAY32_FLOAT)
    		type = "GRAY32_FLOAT";
    	else if (fileType==Constants.RGB)
    		type = "RGB";
    	if (type!=null)
    		code += "fi.fileType = FileInfo."+type+";\n"; 
    	code += "fi.width = "+width+";\n";
    	code += "fi.height = "+height+";\n";
    	if (nImages>1)
			code += "fi.nImages = "+nImages+";\n";  	
    	if (getOffset()>0)
			code += "fi.longOffset = "+getOffset()+";\n";  	
    	if (intelByteOrder)
			code += "fi.intelByteOrder = true;\n";  	
    	return code;
    }

    private String getType() {
    	switch (fileType) {
			case Constants.GRAY8: return "byte";
			case Constants.GRAY16_SIGNED: return "short";
			case Constants.GRAY16_UNSIGNED: return "ushort";
			case Constants.GRAY32_INT: return "int";
			case Constants.GRAY32_UNSIGNED: return "uint";
			case Constants.GRAY32_FLOAT: return "float";
			case Constants.COLOR8: return "byte(lut)";
			case Constants.RGB: return "RGB";
			case Constants.RGB_PLANAR: return "RGB(p)";
			case Constants.RGB48: return "RGB48";
			case Constants.BITMAP: return "bitmap";
			case Constants.ARGB: return "ARGB";
			case Constants.ABGR: return "ABGR";
			case Constants.BGR: return "BGR";
			case Constants.BARG: return "BARG";
			case Constants.CMYK: return "CMYK";
			case Constants.GRAY64_FLOAT: return "double";
			case Constants.RGB48_PLANAR: return "RGB48(p)";
			default: return "";
    	}
    }

	public synchronized Object clone() {
		try {return super.clone();}
		catch (CloneNotSupportedException e) {return null;}
	}

}
