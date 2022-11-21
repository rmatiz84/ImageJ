package ij.io;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;

import ij.IJ;
import ij.util.Tools;


public class TiffMaster {
	
	// start
	// tags
	public static final int NEW_SUBFILE_TYPE = 254;
	public static final int IMAGE_WIDTH = 256;
	public static final int IMAGE_LENGTH = 257;
	public static final int BITS_PER_SAMPLE = 258;
	public static final int COMPRESSION = 259;
	public static final int PHOTO_INTERP = 262;
	public static final int IMAGE_DESCRIPTION = 270;
	public static final int STRIP_OFFSETS = 273;
	public static final int ORIENTATION = 274;
	public static final int SAMPLES_PER_PIXEL = 277;
	public static final int ROWS_PER_STRIP = 278;
	public static final int STRIP_BYTE_COUNT = 279;
	public static final int X_RESOLUTION = 282;
	public static final int Y_RESOLUTION = 283;
	public static final int PLANAR_CONFIGURATION = 284;
	public static final int RESOLUTION_UNIT = 296;
	public static final int SOFTWARE = 305;
	public static final int DATE_TIME = 306;
	public static final int ARTIST = 315;
	public static final int HOST_COMPUTER = 316;
	public static final int PREDICTOR = 317;
	public static final int COLOR_MAP = 320;
	public static final int TILE_WIDTH = 322;
	public static final int SAMPLE_FORMAT = 339;
	public static final int JPEG_TABLES = 347;
	public static final int METAMORPH1 = 33628;
	public static final int METAMORPH2 = 33629;
	public static final int IPLAB = 34122;
	public static final int NIH_IMAGE_HDR = 43314;
	public static final int META_DATA_BYTE_COUNTS = 50838; // private tag registered with Adobe
	public static final int META_DATA = 50839; // private tag registered with Adobe
	
	//constants
	static final int UNSIGNED = 1;
	static final int SIGNED = 2;
	static final int FLOATING_POINT = 3;

	//field types
	static final int SHORT = 3;
	static final int LONG = 4;

	// metadata types
	static final int MAGIC_NUMBER = 0x494a494a;  // "IJIJ"
	static final int INFO = 0x696e666f;  // "info" (Info image property)
	static final int LABELS = 0x6c61626c;  // "labl" (slice labels)
	static final int RANGES = 0x72616e67;  // "rang" (display ranges)
	static final int LUTS = 0x6c757473;    // "luts" (channel LUTs)
	static final int PLOT = 0x706c6f74;    // "plot" (serialized plot)
	static final int ROI = 0x726f6920;     // "roi " (ROI)
	static final int OVERLAY = 0x6f766572; // "over" (overlay)
	static final int PROPERTIES = 0x70726f70; // "prop" (properties)
	
	private String directory;
	private String name;
	private String url;
	protected RandomAccessStream in;
	protected boolean debugMode;
	//private boolean littleEndian;
	private String dInfo;
	private int ifdCount;
	private int[] metaDataCounts;
	private String tiffMetadata;
	// private int photoInterp;
	
	
	public TiffMaster(String directory, String name) {
		if (directory==null)
			directory = "";
		directory = IJ.addSeparator(directory);
		this.directory = directory;
		this.name = name;
	}

	public TiffMaster(InputStream in, String name) {
		directory = "";
		this.name = name;
		url = "";
		this.in = new RandomAccessStream(in);
	}

	final int getInt() throws IOException {
		int b1 = in.read();
		int b2 = in.read();
		int b3 = in.read();
		int b4 = in.read();
		if (littleEndian)
			return ((b4 << 24) + (b3 << 16) + (b2 << 8) + (b1 << 0));
		else
			return ((b1 << 24) + (b2 << 16) + (b3 << 8) + b4);
	}
	
	final long getUnsignedInt() throws IOException {
		return (long)getInt()&0xffffffffL;
	}

	final int getShort() throws IOException {
		int b1 = in.read();
		int b2 = in.read();
		if (littleEndian)
			return ((b2<<8) + b1);
		else
			return ((b1<<8) + b2);
	}

    final long readLong() throws IOException {
    	if (littleEndian)
        	return ((long)getInt()&0xffffffffL) + ((long)getInt()<<32);
        else
			return ((long)getInt()<<32) + ((long)getInt()&0xffffffffL);
        	//return in.read()+(in.read()<<8)+(in.read()<<16)+(in.read()<<24)+(in.read()<<32)+(in.read()<<40)+(in.read()<<48)+(in.read()<<56);
    }

    final double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

	long OpenImageFileHeader() throws IOException {
	// Open 8-byte Image File Header at start of file.
	// Returns the offset in bytes to the first IFD or -1
	// if this is not a valid tiff file.
		int byteOrder = in.readShort();
		if (byteOrder==0x4949) // "II"
			littleEndian = true;
		else if (byteOrder==0x4d4d) // "MM"
			littleEndian = false;
		else {
			in.close();
			return -1;
		}
		int magicNumber = getShort(); // 42
		long offset = ((long)getInt())&0xffffffffL;
		return offset;
	}
		
	int getValue(int fieldType, int count) throws IOException {
		int value = 0;
		int unused;
		if (fieldType==SHORT && count==1) {
			value = getShort();
			unused = getShort();
		} else
			value = getInt();
		return value;
	}	
	
	void getColorMap(long offset, FileInfo fi) throws IOException {
		byte[] colorTable16 = new byte[768*2];
		long saveLoc = in.getLongFilePointer();
		in.seek(offset);
		in.readFully(colorTable16);
		in.seek(saveLoc);
		fi.lutSize = 256;
		fi.reds = new byte[256];
		fi.greens = new byte[256];
		fi.blues = new byte[256];
		int j = 0;
		if (littleEndian) j++;
		int sum = 0;
		for (int i=0; i<256; i++) {
			fi.reds[i] = colorTable16[j];
			sum += fi.reds[i];
			fi.greens[i] = colorTable16[512+j];
			sum += fi.greens[i];
			fi.blues[i] = colorTable16[1024+j];
			sum += fi.blues[i];
			j += 2;
		}
		if (sum!=0 && fi.fileType==Constants.GRAY8)
			fi.fileType = Constants.COLOR8;
	}
	
	byte[] getString(int count, long offset) throws IOException {
		count--; // skip null byte at end of string
		if (count<=3)
			return null;
		byte[] bytes = new byte[count];
		long saveLoc = in.getLongFilePointer();
		in.seek(offset);
		in.readFully(bytes);
		in.seek(saveLoc);
		return bytes;
	}

	/** Save the image description in the specified FileInfo. ImageJ
		saves spatial and density calibration data in this string. For
		stacks, it also saves the number of images to avoid having to
		decode an IFD for each image. */
	public void saveImageDescription(byte[] description, FileInfo fi) {
        String id = new String(description);
        boolean createdByImageJ = id.startsWith("ImageJ");
        if (!createdByImageJ)
			saveMetadata(getName(IMAGE_DESCRIPTION), id);
		if (id.length()<7) return;
		fi.description = id;
        int index1 = id.indexOf("images=");
        if (index1>0 && createdByImageJ && id.charAt(7)!='\n') {
            int index2 = id.indexOf("\n", index1);
            if (index2>0) {
                String images = id.substring(index1+7,index2);
                int n = (int)Tools.parseDouble(images, 0.0);
                if (n>1 && fi.compression==Constants.COMPRESSION_NONE)
                	fi.nImages = n;
            }
        }
	}

	public void saveMetadata(String name, String data) {
		if (data==null) return;
        String str = name+": "+data+"\n";
        if (tiffMetadata==null)
        	tiffMetadata = str;
        else
        	tiffMetadata += str;
	}

	void decodeNIHImageHeader(int offset, FileInfo fi) throws IOException {
		long saveLoc = in.getLongFilePointer();
		
		in.seek(offset+12);
		int version = in.readShort();
		
		in.seek(offset+160);
		double scale = in.readDouble();
		if (version>106 && scale!=0.0) {
			fi.pixelWidth = 1.0/scale;
			fi.pixelHeight = fi.pixelWidth;
		} 

		// spatial calibration
		in.seek(offset+172);
		int units = in.readShort();
		if (version<=153) units += 5;
		switch (units) {
			case 5: fi.unit = "nanometer"; break;
			case 6: fi.unit = "micrometer"; break;
			case 7: fi.unit = "mm"; break;
			case 8: fi.unit = "cm"; break;
			case 9: fi.unit = "meter"; break;
			case 10: fi.unit = "km"; break;
			case 11: fi.unit = "inch"; break;
			case 12: fi.unit = "ft"; break;
			case 13: fi.unit = "mi"; break;
		}

		// density calibration
		in.seek(offset+182);
		int fitType = in.read();
		int unused = in.read();
		int nCoefficients = in.readShort();
		if (fitType==11) {
			fi.calibrationFunction = 21; //Calibration.UNCALIBRATED_OD
			fi.valueUnit = "U. OD";
		} else if (fitType>=0 && fitType<=8 && nCoefficients>=1 && nCoefficients<=5) {
			switch (fitType) {
				case 0: fi.calibrationFunction = 0; break; //Calibration.STRAIGHT_LINE
				case 1: fi.calibrationFunction = 1; break; //Calibration.POLY2
				case 2: fi.calibrationFunction = 2; break; //Calibration.POLY3
				case 3: fi.calibrationFunction = 3; break; //Calibration.POLY4
				case 5: fi.calibrationFunction = 4; break; //Calibration.EXPONENTIAL
				case 6: fi.calibrationFunction = 5; break; //Calibration.POWER
				case 7: fi.calibrationFunction = 6; break; //Calibration.LOG
				case 8: fi.calibrationFunction = 10; break; //Calibration.RODBARD2 (NIH Image)
			}
			fi.coefficients = new double[nCoefficients];
			for (int i=0; i<nCoefficients; i++) {
				fi.coefficients[i] = in.readDouble();
			}
			in.seek(offset+234);
			int size = in.read();
			StringBuffer sb = new StringBuffer();
			if (size>=1 && size<=16) {
				for (int i=0; i<size; i++)
					sb.append((char)(in.read()));
				fi.valueUnit = new String(sb);
			} else
				fi.valueUnit = " ";
		}
			
		in.seek(offset+260);
		int nImages = in.readShort();
		if (nImages>=2 && (fi.fileType==Constants.GRAY8||fi.fileType==Constants.COLOR8)) {
			fi.nImages = nImages;
			fi.pixelDepth = in.readFloat();	//SliceSpacing
			int skip = in.readShort();		//CurrentSlice
			fi.frameInterval = in.readFloat();
		}
			
		in.seek(offset+272);
		float aspectRatio = in.readFloat();
		if (version>140 && aspectRatio!=0.0)
			fi.pixelHeight = fi.pixelWidth/aspectRatio;
		
		in.seek(saveLoc);
	}
	
	void dumpTag(int tag, int count, int value, FileInfo fi) {
		long lvalue = ((long)value)&0xffffffffL;
		String name = getName(tag);
		String cs = (count==1)?"":", count=" + count;
		dInfo += "    " + tag + ", \"" + name + "\", value=" + lvalue + cs + "\n";
		//ij.IJ.log(tag + ", \"" + name + "\", value=" + value + cs + "\n");
	}

	String getName(int tag) {
		String name;
		switch (tag) {
			case NEW_SUBFILE_TYPE: name="NewSubfileType"; break;
			case IMAGE_WIDTH: name="ImageWidth"; break;
			case IMAGE_LENGTH: name="ImageLength"; break;
			case STRIP_OFFSETS: name="StripOffsets"; break;
			case ORIENTATION: name="Orientation"; break;
			case PHOTO_INTERP: name="PhotoInterp"; break;
			case IMAGE_DESCRIPTION: name="ImageDescription"; break;
			case BITS_PER_SAMPLE: name="BitsPerSample"; break;
			case SAMPLES_PER_PIXEL: name="SamplesPerPixel"; break;
			case ROWS_PER_STRIP: name="RowsPerStrip"; break;
			case STRIP_BYTE_COUNT: name="StripByteCount"; break;
			case X_RESOLUTION: name="XResolution"; break;
			case Y_RESOLUTION: name="YResolution"; break;
			case RESOLUTION_UNIT: name="ResolutionUnit"; break;
			case SOFTWARE: name="Software"; break;
			case DATE_TIME: name="DateTime"; break;
			case ARTIST: name="Artist"; break;
			case HOST_COMPUTER: name="HostComputer"; break;
			case PLANAR_CONFIGURATION: name="PlanarConfiguration"; break;
			case COMPRESSION: name="Compression"; break; 
			case PREDICTOR: name="Predictor"; break; 
			case COLOR_MAP: name="ColorMap"; break; 
			case SAMPLE_FORMAT: name="SampleFormat"; break; 
			case JPEG_TABLES: name="JPEGTables"; break; 
			case NIH_IMAGE_HDR: name="NIHImageHeader"; break; 
			case META_DATA_BYTE_COUNTS: name="MetaDataByteCounts"; break; 
			case META_DATA: name="MetaData"; break; 
			default: name="???"; break;
		}
		return name;
	}

	double getRational(long loc) throws IOException {
		long saveLoc = in.getLongFilePointer();
		in.seek(loc);
		double numerator = getUnsignedInt();
		double denominator = getUnsignedInt();
		in.seek(saveLoc);
		if (denominator!=0.0)
			return numerator/denominator;
		else
			return 0.0;
	}
	
	FileInfo OpenIFD() throws IOException {
	// Get Image File Directory data
		int tag, fieldType, count, value;
		int nEntries = getShort();
		if (nEntries<1 || nEntries>1000)
			return null;
		ifdCount++;
		if ((ifdCount%50)==0 && ifdCount>0)
			ij.IJ.showStatus("Opening IFDs: "+ifdCount);
		FileInfo fi = new FileInfo();
		fi.fileType = Constants.BITMAP;  //BitsPerSample defaults to 1
		for (int i=0; i<nEntries; i++) {
			tag = getShort();
			fieldType = getShort();
			count = getInt();
			value = getValue(fieldType, count);
			long lvalue = ((long)value)&0xffffffffL;
			if (debugMode && ifdCount<10) dumpTag(tag, count, value, fi);
			switch (tag) {
				case IMAGE_WIDTH: 
					fi.width = value;
					fi.intelByteOrder = littleEndian;
					break;
				case IMAGE_LENGTH: 
					fi.height = value;
					break;
 				case STRIP_OFFSETS:
					if (count==1)
						fi.stripOffsets = new int[] {value};
					else {
						long saveLoc = in.getLongFilePointer();
						in.seek(lvalue);
						fi.stripOffsets = new int[count];
						for (int c=0; c<count; c++)
							fi.stripOffsets[c] = getInt();
						in.seek(saveLoc);
					}
					fi.offset = count>0?fi.stripOffsets[0]:value;
					if (count>1 && (((long)fi.stripOffsets[count-1])&0xffffffffL)<(((long)fi.stripOffsets[0])&0xffffffffL))
						fi.offset = fi.stripOffsets[count-1];
					break;
				case STRIP_BYTE_COUNT:
					if (count==1)
						fi.stripLengths = new int[] {value};
					else {
						long saveLoc = in.getLongFilePointer();
						in.seek(lvalue);
						fi.stripLengths = new int[count];
						for (int c=0; c<count; c++) {
							if (fieldType==SHORT)
								fi.stripLengths[c] = getShort();
							else
								fi.stripLengths[c] = getInt();
						}
						in.seek(saveLoc);
					}
					break;
 				case PHOTO_INTERP:
 					photoInterp = value;
 					fi.whiteIsZero = value==0;
					break;
				case BITS_PER_SAMPLE:
						if (count==1) {
							if (value==8)
								fi.fileType = Constants.GRAY8;
							else if (value==16)
								fi.fileType = Constants.GRAY16_UNSIGNED;
							else if (value==32)
								fi.fileType = Constants.GRAY32_INT;
							else if (value==12)
								fi.fileType = Constants.GRAY12_UNSIGNED;
							else if (value==1)
								fi.fileType = Constants.BITMAP;
							else
								error("Unsupported BitsPerSample: " + value);
						} else if (count>1) {
							long saveLoc = in.getLongFilePointer();
							in.seek(lvalue);
							int bitDepth = getShort();
							if (bitDepth==8)
								fi.fileType = Constants.GRAY8;
							else if (bitDepth==16)
								fi.fileType = Constants.GRAY16_UNSIGNED;
							else
								error("ImageJ cannot open interleaved "+bitDepth+"-bit images.");
							in.seek(saveLoc);
						}
						break;
				case SAMPLES_PER_PIXEL:
					fi.samplesPerPixel = value;
					if (value==3 && fi.fileType==Constants.GRAY8)
						fi.fileType = Constants.RGB;
					else if (value==3 && fi.fileType==Constants.GRAY16_UNSIGNED)
						fi.fileType = Constants.RGB48;
					else if (value==4 && fi.fileType==Constants.GRAY8)
						fi.fileType = photoInterp==5?Constants.CMYK:Constants.ARGB;
					else if (value==4 && fi.fileType==Constants.GRAY16_UNSIGNED) {
						fi.fileType = Constants.RGB48;
						if (photoInterp==5)  //assume cmyk
							fi.whiteIsZero = true;
					}
					break;
				case ROWS_PER_STRIP:
					fi.rowsPerStrip = value;
					break;
				case X_RESOLUTION:
					double xScale = getRational(lvalue); 
					if (xScale!=0.0) fi.pixelWidth = 1.0/xScale; 
					break;
				case Y_RESOLUTION:
					double yScale = getRational(lvalue); 
					if (yScale!=0.0) fi.pixelHeight = 1.0/yScale; 
					break;
				case RESOLUTION_UNIT:
					if (value==1&&fi.unit==null)
						fi.unit = " ";
					else if (value==2) {
						if (fi.pixelWidth==1.0/72.0) {
							fi.pixelWidth = 1.0;
							fi.pixelHeight = 1.0;
						} else
							fi.unit = "inch";
					} else if (value==3)
						fi.unit = "cm";
					break;
				case PLANAR_CONFIGURATION:  // 1=chunky, 2=planar
					if (value==2 && fi.fileType==Constants.RGB48)
							 fi.fileType = Constants.RGB48_PLANAR;
					else if (value==2 && fi.fileType==Constants.RGB)
						fi.fileType = Constants.RGB_PLANAR;
					else if (value!=2 && !(fi.samplesPerPixel==1||fi.samplesPerPixel==3||fi.samplesPerPixel==4)) {
						String msg = "Unsupported SamplesPerPixel: " + fi.samplesPerPixel;
						error(msg);
					}
					break;
				case COMPRESSION:
					if (value==5)  {// LZW compression
						fi.compression = Constants.LZW;
						if (fi.fileType==Constants.GRAY12_UNSIGNED)
							error("ImageJ cannot open 12-bit LZW-compressed TIFFs");
					} else if (value==32773)  // PackBits compression
						fi.compression = Constants.PACK_BITS;
					else if (value==32946 || value==8) //8=Adobe deflate
						fi.compression = Constants.ZIP;
					else if (value!=1 && value!=0 && !(value==7&&fi.width<500)) {
						// don't abort with Spot camera compressed (7) thumbnails
						// otherwise, this is an unknown compression type
						fi.compression = Constants.COMPRESSION_UNKNOWN;
						error("ImageJ cannot open TIFF files " +
							"compressed in this fashion ("+value+")");
					}
					break;
				case SOFTWARE: case DATE_TIME: case HOST_COMPUTER: case ARTIST:
					if (ifdCount==1) {
						byte[] bytes = getString(count, lvalue);
						String s = bytes!=null?new String(bytes):null;
						saveMetadata(getName(tag), s);
					}
					break;
				case PREDICTOR:
					if (value==2 && fi.compression==Constants.LZW)
						fi.compression = Constants.LZW_WITH_DIFFERENCING;
					if (value==3)
						IJ.log("TiffDecoder: unsupported predictor value of 3");
					break;
				case COLOR_MAP: 
					if (count==768)
						getColorMap(lvalue, fi);
					break;
				case TILE_WIDTH:
					error("ImageJ cannot open tiled TIFFs.\nTry using the Bio-Formats plugin.");
					break;
				case SAMPLE_FORMAT:
					if (fi.fileType==Constants.GRAY32_INT && value==FLOATING_POINT)
						fi.fileType = Constants.GRAY32_FLOAT;
					if (fi.fileType==Constants.GRAY16_UNSIGNED) {
						if (value==SIGNED)
							fi.fileType = Constants.GRAY16_SIGNED;
						if (value==FLOATING_POINT)
							error("ImageJ cannot open 16-bit float TIFFs");
					}
					break;
				case JPEG_TABLES:
					if (fi.compression==Constants.JPEG)
						error("Cannot open JPEG-compressed TIFFs with separate tables");
					break;
				case IMAGE_DESCRIPTION: 
					if (ifdCount==1) {
						byte[] s = getString(count, lvalue);
						if (s!=null) saveImageDescription(s,fi);
					}
					break;
				case ORIENTATION:
					fi.nImages = 0; // file not created by ImageJ so look at all the IFDs
					break;
				case METAMORPH1: case METAMORPH2:
					if ((name.indexOf(".STK")>0||name.indexOf(".stk")>0) && fi.compression==Constants.COMPRESSION_NONE) {
						if (tag==METAMORPH2)
							fi.nImages=count;
						else
							fi.nImages=9999;
					}
					break;
				case IPLAB: 
					fi.nImages=value;
					break;
				case NIH_IMAGE_HDR: 
					if (count==256)
						decodeNIHImageHeader(value, fi);
					break;
 				case META_DATA_BYTE_COUNTS: 
					long saveLoc = in.getLongFilePointer();
					in.seek(lvalue);
					metaDataCounts = new int[count];
					for (int c=0; c<count; c++)
						metaDataCounts[c] = getInt();
					in.seek(saveLoc);
					break;
 				case META_DATA: 
 					getMetaData(value, fi);
 					break;
				default:
					if (tag>10000 && tag<32768 && ifdCount>1)
						return null;
			}
		}
		fi.fileFormat = Constants.TIFF;
		fi.fileName = name;
		fi.directory = directory;
		if (url!=null)
			fi.url = url;
		return fi;
	}

	void getMetaData(int loc, FileInfo fi) throws IOException {
		if (metaDataCounts==null || metaDataCounts.length==0)
			return;
		int maxTypes = 10;
		long saveLoc = in.getLongFilePointer();
		in.seek(loc);
		int n = metaDataCounts.length;
		int hdrSize = metaDataCounts[0];
		if (hdrSize<12 || hdrSize>804) {
			in.seek(saveLoc);
			return;
		}
		int magicNumber = getInt();
		if (magicNumber!=MAGIC_NUMBER)  { // "IJIJ"
			in.seek(saveLoc);
			return;
		}
		int nTypes = (hdrSize-4)/8;
		int[] types = new int[nTypes];
		int[] counts = new int[nTypes];		
		if (debugMode) {
			dInfo += "Metadata:\n";
			dInfo += "   Entries: "+(metaDataCounts.length-1)+"\n";
			dInfo += "   Types: "+nTypes+"\n";
		}
		int extraMetaDataEntries = 0;
		int index = 1;
		for (int i=0; i<nTypes; i++) {
			types[i] = getInt();
			counts[i] = getInt();
			if (types[i]<0xffffff)
				extraMetaDataEntries += counts[i];
			if (debugMode) {
				String id = "unknown";
				if (types[i]==INFO) id = "Info property";
				if (types[i]==LABELS) id = "slice labels";
				if (types[i]==RANGES) id = "display ranges";
				if (types[i]==LUTS) id = "luts";
				if (types[i]==PLOT) id = "plot";
				if (types[i]==ROI) id = "roi";
				if (types[i]==OVERLAY) id = "overlay";
				if (types[i]==PROPERTIES) id = "properties";
				int len = metaDataCounts[index];
				int count = counts[i];
				index += count;
				if (index>=metaDataCounts.length) index=1;
				String lenstr = count==1?", length=":", length[0]=";
				dInfo += "   "+i+", type="+id+", count="+count+lenstr+len+"\n";
			}
		}
		fi.metaDataTypes = new int[extraMetaDataEntries];
		fi.metaData = new byte[extraMetaDataEntries][];
		int start = 1;
		int eMDindex = 0;
		for (int i=0; i<nTypes; i++) {
			if (types[i]==INFO)
				getInfoProperty(start, fi);
			else if (types[i]==LABELS)
				getSliceLabels(start, start+counts[i]-1, fi);
			else if (types[i]==RANGES)
				getDisplayRanges(start, fi);
			else if (types[i]==LUTS)
				getLuts(start, start+counts[i]-1, fi);
			else if (types[i]==PLOT)
				getPlot(start, fi);
			else if (types[i]==ROI)
				getRoi(start, fi);
			else if (types[i]==OVERLAY)
				getOverlay(start, start+counts[i]-1, fi);
			else if (types[i]==PROPERTIES)
				getProperties(start, start+counts[i]-1, fi);
			else if (types[i]<0xffffff) {
				for (int j=start; j<start+counts[i]; j++) { 
					int len = metaDataCounts[j]; 
					fi.metaData[eMDindex] = new byte[len]; 
					in.readFully(fi.metaData[eMDindex], len); 
					fi.metaDataTypes[eMDindex] = types[i]; 
					eMDindex++; 
				} 
			} else
				skipUnknownType(start, start+counts[i]-1);
			start += counts[i];
		}
		in.seek(saveLoc);
	}

	void getInfoProperty(int first, FileInfo fi) throws IOException {
		int len = metaDataCounts[first];
	    byte[] buffer = new byte[len];
		in.readFully(buffer, len);
		len /= 2;
		char[] chars = new char[len];
		if (littleEndian) {
			for (int j=0, k=0; j<len; j++)
				chars[j] = (char)(buffer[k++]&255 + ((buffer[k++]&255)<<8));
		} else {
			for (int j=0, k=0; j<len; j++)
				chars[j] = (char)(((buffer[k++]&255)<<8) + (buffer[k++]&255));
		}
		fi.info = new String(chars);
	}

	void getSliceLabels(int first, int last, FileInfo fi) throws IOException {
		fi.sliceLabels = new String[last-first+1];
	    int index = 0;
	    byte[] buffer = new byte[metaDataCounts[first]];
		for (int i=first; i<=last; i++) {
			int len = metaDataCounts[i];
			if (len>0) {
				if (len>buffer.length)
					buffer = new byte[len];
				in.readFully(buffer, len);
				len /= 2;
				char[] chars = new char[len];
				if (littleEndian) {
					for (int j=0, k=0; j<len; j++)
						chars[j] = (char)(buffer[k++]&255 + ((buffer[k++]&255)<<8));
				} else {
					for (int j=0, k=0; j<len; j++)
						chars[j] = (char)(((buffer[k++]&255)<<8) + (buffer[k++]&255));
				}
				fi.sliceLabels[index++] = new String(chars);
				//ij.IJ.log(i+"  "+fi.sliceLabels[i-1]+"  "+len);
			} else
				fi.sliceLabels[index++] = null;
		}
	}

	void getDisplayRanges(int first, FileInfo fi) throws IOException {
		int n = metaDataCounts[first]/8;
		fi.displayRanges = new double[n];
		for (int i=0; i<n; i++)
			fi.displayRanges[i] = readDouble();
	}

	void getLuts(int first, int last, FileInfo fi) throws IOException {
		fi.channelLuts = new byte[last-first+1][];
	    int index = 0;
		for (int i=first; i<=last; i++) {
			int len = metaDataCounts[i];
			fi.channelLuts[index] = new byte[len];
            in.readFully(fi.channelLuts[index], len);
            index++;
		}
	}

	void getRoi(int first, FileInfo fi) throws IOException {
		int len = metaDataCounts[first];
		fi.roi = new byte[len]; 
		in.readFully(fi.roi, len); 
	}

	void getPlot(int first, FileInfo fi) throws IOException {
		int len = metaDataCounts[first];
		fi.plot = new byte[len];
		in.readFully(fi.plot, len);
	}

	void getOverlay(int first, int last, FileInfo fi) throws IOException {
		fi.overlay = new byte[last-first+1][];
	    int index = 0;
		for (int i=first; i<=last; i++) {
			int len = metaDataCounts[i];
			fi.overlay[index] = new byte[len];
            in.readFully(fi.overlay[index], len);
            index++;
		}
	}

	void getProperties(int first, int last, FileInfo fi) throws IOException {
		fi.properties = new String[last-first+1];
	    int index = 0;
	    byte[] buffer = new byte[metaDataCounts[first]];
		for (int i=first; i<=last; i++) {
			int len = metaDataCounts[i];
			if (len>buffer.length)
				buffer = new byte[len];
			in.readFully(buffer, len);
			len /= 2;
			char[] chars = new char[len];
			if (littleEndian) {
				for (int j=0, k=0; j<len; j++)
					chars[j] = (char)(buffer[k++]&255 + ((buffer[k++]&255)<<8));
			} else {
				for (int j=0, k=0; j<len; j++)
					chars[j] = (char)(((buffer[k++]&255)<<8) + (buffer[k++]&255));
			}
			fi.properties[index++] = new String(chars);
		}
	}

	void error(String message) throws IOException {
		if (in!=null) in.close();
		throw new IOException(message);
	}
	
	void skipUnknownType(int first, int last) throws IOException {
	    byte[] buffer = new byte[metaDataCounts[first]];
		for (int i=first; i<=last; i++) {
			int len = metaDataCounts[i];
            if (len>buffer.length)
                buffer = new byte[len];
            in.readFully(buffer, len);
		}
	}

	public void enableDebugging() {
		debugMode = true;
	}
		
	public FileInfo[] getTiffInfo() throws IOException {
		long ifdOffset;
		ArrayList list = new ArrayList();
		if (in==null)
			in = new RandomAccessStream(new RandomAccessFile(new File(directory+name), "r"));
		ifdOffset = OpenImageFileHeader();
		if (ifdOffset<0L) {
			in.close();
			return null;
		}
		if (debugMode) dInfo = "\n  " + name + ": opening\n";
		while (ifdOffset>0L) {
			in.seek(ifdOffset);
			FileInfo fi = OpenIFD();
			if (fi!=null) {
				list.add(fi);
				ifdOffset = ((long)getInt())&0xffffffffL;
			} else
				ifdOffset = 0L;
			if (debugMode && ifdCount<10) dInfo += "nextIFD=" + ifdOffset + "\n";
			if (fi!=null && fi.nImages>1)
				ifdOffset = 0L;   // ignore extra IFDs in ImageJ and NIH Image stacks
		}
		if (list.size()==0) {
			in.close();
			return null;
		} else {
			FileInfo[] info = (FileInfo[])list.toArray(new FileInfo[list.size()]);
			if (debugMode) info[0].debugInfo = dInfo;
			if (url!=null) {
				in.seek(0);
				info[0].inputStream = in;
			} else
				in.close();
			if (info[0].info==null)
				info[0].info = tiffMetadata;
			FileInfo fi = info[0];
			if (fi.fileType==Constants.GRAY16_UNSIGNED && fi.description==null)
				fi.lutSize = 0; // ignore troublesome non-ImageJ 16-bit LUTs
			if (debugMode) {
				int n = info.length;
				fi.debugInfo += "number of IFDs: "+ n + "\n";
				fi.debugInfo += "offset to first image: "+fi.getOffset()+ "\n";
				fi.debugInfo += "gap between images: "+getGapInfo(info) + "\n";
				fi.debugInfo += "little-endian byte order: "+fi.intelByteOrder + "\n";
			}
			return info;
		}
	}
	
	String getGapInfo(FileInfo[] fi) {
		if (fi.length<2) return "0";
		long minGap = Long.MAX_VALUE;
		long maxGap = -Long.MAX_VALUE;
		for (int i=1; i<fi.length; i++) {
			long gap = fi[i].getOffset()-fi[i-1].getOffset();
			if (gap<minGap) minGap = gap;
			if (gap>maxGap) maxGap = gap;
		}
		long imageSize = fi[0].width*fi[0].height*fi[0].getBytesPerPixel();
		minGap -= imageSize;
		maxGap -= imageSize;
		if (minGap==maxGap)
			return ""+minGap;
		else 
			return "varies ("+minGap+" to "+maxGap+")";
	}

	
	
	
	// end
	
	static final int HDR_SIZE = 8;
	static final int MAP_SIZE = 768; // in 16-bit words
	static final int BPS_DATA_SIZE = 6;
	static final int SCALE_DATA_SIZE = 16;
		
	private FileInfo fi;
	private int bitsPerSample;
	private int photoInterp;
	private int samplesPerPixel;
	private int nEntries;
	private int ifdSize;
	private long imageOffset;
	private int imageSize;
	private long stackSize;
	private byte[] description;
	private int metaDataSize;
	private int nMetaDataTypes;
	private int nMetaDataEntries;
	private int nSliceLabels;
	private int extraMetaDataEntries;
	private int scaleSize;
	private boolean littleEndian = ij.Prefs.intelByteOrder;
	private byte buffer[] = new byte[8];
	private int colorMapSize = 0;

		
	public TiffMaster (FileInfo fi) {
		this.fi = fi;
		fi.intelByteOrder = littleEndian;
		bitsPerSample = 8;
		samplesPerPixel = 1;
		nEntries = 10;
		int bytesPerPixel = 1;
		int bpsSize = 0;

		switch (fi.fileType) {
			case Constants.GRAY8:
				photoInterp = fi.whiteIsZero?0:1;
				break;
			case Constants.GRAY16_UNSIGNED:
			case Constants.GRAY16_SIGNED:
				bitsPerSample = 16;
				photoInterp = fi.whiteIsZero?0:1;
				if (fi.lutSize>0) {
					nEntries++;
					colorMapSize = MAP_SIZE*2;
				}
				bytesPerPixel = 2;
				break;
			case Constants.GRAY32_FLOAT:
				bitsPerSample = 32;
				photoInterp = fi.whiteIsZero?0:1;
				if (fi.lutSize>0) {
					nEntries++;
					colorMapSize = MAP_SIZE*2;
				}
				bytesPerPixel = 4;
				break;
			case Constants.RGB:
				photoInterp = 2;
				samplesPerPixel = 3;
				bytesPerPixel = 3;
				bpsSize = BPS_DATA_SIZE;
				break;
			case Constants.RGB48:
				bitsPerSample = 16;
				photoInterp = 2;
				samplesPerPixel = 3;
				bytesPerPixel = 6;
				fi.nImages /= 3;
				bpsSize = BPS_DATA_SIZE;
				break;
			case Constants.COLOR8:
				photoInterp = 3;
				nEntries++;
				colorMapSize = MAP_SIZE*2;
				break;
			default:
				photoInterp = 0;
		}
		if (fi.unit!=null && fi.pixelWidth!=0 && fi.pixelHeight!=0)
			nEntries += 3; // XResolution, YResolution and ResolutionUnit
		if (fi.fileType==Constants.GRAY32_FLOAT)
			nEntries++; // SampleFormat tag
		makeDescriptionString();
		if (description!=null)
			nEntries++;  // ImageDescription tag
		long size = (long)fi.width*fi.height*bytesPerPixel;
		imageSize = size<=0xffffffffL?(int)size:0;
		stackSize = (long)imageSize*fi.nImages;
		metaDataSize = getMetaDataSize();
		if (metaDataSize>0)
			nEntries += 2; // MetaData & MetaDataCounts
		ifdSize = 2 + nEntries*12 + 4;
		int descriptionSize = description!=null?description.length:0;
		scaleSize = fi.unit!=null && fi.pixelWidth!=0 && fi.pixelHeight!=0?SCALE_DATA_SIZE:0;
		imageOffset = HDR_SIZE+ifdSize+bpsSize+descriptionSize+scaleSize+colorMapSize + nMetaDataEntries*4 + metaDataSize;
		fi.offset = (int)imageOffset;
		//ij.IJ.log(imageOffset+", "+ifdSize+", "+bpsSize+", "+descriptionSize+", "+scaleSize+", "+colorMapSize+", "+nMetaDataEntries*4+", "+metaDataSize);
	}
	
	/** Saves the image as a TIFF file. The OutputStream is not closed.
		The fi.pixels field must contain the image data. If fi.nImages>1
		then fi.pixels must be a 2D array. The fi.offset field is ignored. */
	public void write(OutputStream out) throws IOException {
		writeHeader(out);
		long nextIFD = 0L;
		if (fi.nImages>1)
			nextIFD = imageOffset+stackSize;
		boolean bigTiff = nextIFD+fi.nImages*ifdSize>=0xffffffffL;
		if (bigTiff)
			nextIFD = 0L;
		writeIFD(out, (int)imageOffset, (int)nextIFD);
		if (fi.fileType==Constants.RGB||fi.fileType==Constants.RGB48)
			writeBitsPerPixel(out);
		if (description!=null)
			writeDescription(out);
		if (scaleSize>0)
			writeScale(out);
		if (colorMapSize>0)
			writeColorMap(out);
		if (metaDataSize>0)
			writeMetaData(out);
		new ImageWriter(fi).write(out);
		if (nextIFD>0L) {
			int ifdSize2 = ifdSize;
			if (metaDataSize>0) {
				metaDataSize = 0;
				nEntries -= 2;
				ifdSize2 -= 2*12;
			}
			for (int i=2; i<=fi.nImages; i++) {
				if (i==fi.nImages)
					nextIFD = 0;
				else
					nextIFD += ifdSize2;
				imageOffset += imageSize;
				writeIFD(out, (int)imageOffset, (int)nextIFD);
			}
		} else if (bigTiff)
				ij.IJ.log("Stack is larger than 4GB. Most TIFF readers will only open the first image. Use this information to open as raw:\n"+fi);
	}
	
	public void write(DataOutputStream out) throws IOException {
		write((OutputStream)out);
	}

	int getMetaDataSize() {
		nSliceLabels = 0;
		nMetaDataEntries = 0;
		int size = 0;
		int nTypes = 0;
		if (fi.info!=null && fi.info.length()>0) {
			nMetaDataEntries = 1;
			size = fi.info.length()*2;
			nTypes++;
		}
		if (fi.sliceLabels!=null) {
			int max = Math.min(fi.sliceLabels.length, fi.nImages);
			boolean isNonNullLabel = false;
			for (int i=0; i<max; i++) {
				if (fi.sliceLabels[i]!=null && fi.sliceLabels[i].length()>0) {
					isNonNullLabel = true;
					break;
				}
			}
			if (isNonNullLabel) {
				for (int i=0; i<max; i++) {
					nSliceLabels++;
					if (fi.sliceLabels[i]!=null)
						size += fi.sliceLabels[i].length()*2;
				}
				if (nSliceLabels>0) nTypes++;
				nMetaDataEntries += nSliceLabels;
			}
		}

		if (fi.displayRanges!=null) {
			nMetaDataEntries++;
			size += fi.displayRanges.length*8;
			nTypes++;
		}

		if (fi.channelLuts!=null) {
			for (int i=0; i<fi.channelLuts.length; i++) {
                if (fi.channelLuts[i]!=null)
                    size += fi.channelLuts[i].length;
            }
			nTypes++;
			nMetaDataEntries += fi.channelLuts.length;
		}

		if (fi.plot!=null) {
			nMetaDataEntries++;
			size += fi.plot.length;
			nTypes++;
		}

		if (fi.roi!=null) {
			nMetaDataEntries++;
			size += fi.roi.length;
			nTypes++;
		}

		if (fi.overlay!=null) {
			for (int i=0; i<fi.overlay.length; i++) {
				if (fi.overlay[i]!=null)
					size += fi.overlay[i].length;
			}
			nTypes++;
			nMetaDataEntries += fi.overlay.length;
		}

		if (fi.properties!=null) {
			for (int i=0; i<fi.properties.length; i++)
				size += fi.properties[i].length()*2;
			nTypes++;
			nMetaDataEntries += fi.properties.length;
		}

		if (fi.metaDataTypes!=null && fi.metaData!=null && fi.metaData[0]!=null
		&& fi.metaDataTypes.length==fi.metaData.length) {
			extraMetaDataEntries = fi.metaData.length;
			nTypes += extraMetaDataEntries;
			nMetaDataEntries += extraMetaDataEntries;
			for (int i=0; i<extraMetaDataEntries; i++) {
                if (fi.metaData[i]!=null)
                    size += fi.metaData[i].length;
            }
		}
		if (nMetaDataEntries>0) nMetaDataEntries++; // add entry for header
		int hdrSize = 4 + nTypes*8;
		if (size>0) size += hdrSize;
		nMetaDataTypes = nTypes;
		return size;
	}
	
	/** Writes the 8-byte image file header. */
	void writeHeader(OutputStream out) throws IOException {
		byte[] hdr = new byte[8];
		if (littleEndian) {
			hdr[0] = 73; // "II" (Intel byte order)
			hdr[1] = 73;
			hdr[2] = 42;  // 42 (magic number)
			hdr[3] = 0;
			hdr[4] = 8;  // 8 (offset to first IFD)
			hdr[5] = 0;
			hdr[6] = 0;
			hdr[7] = 0;
		} else {
			hdr[0] = 77; // "MM" (Motorola byte order)
			hdr[1] = 77;
			hdr[2] = 0;  // 42 (magic number)
			hdr[3] = 42;
			hdr[4] = 0;  // 8 (offset to first IFD)
			hdr[5] = 0;
			hdr[6] = 0;
			hdr[7] = 8;
		}
		out.write(hdr);
	}
	
	/** Writes one 12-byte IFD entry. */
	void writeEntry(OutputStream out, int tag, int fieldType, int count, int value) throws IOException {
		writeShort(out, tag);
		writeShort(out, fieldType);
		writeInt(out, count);
		if (count==1 && fieldType==SHORT) {
			writeShort(out, value);
			writeShort(out, 0);
		} else
			writeInt(out, value); // may be an offset
	}
	
	/** Writes one IFD (Image File Directory). */
	void writeIFD(OutputStream out, int imageOffset, int nextIFD) throws IOException {	
		int tagDataOffset = HDR_SIZE + ifdSize;
		writeShort(out, nEntries);
		writeEntry(out, NEW_SUBFILE_TYPE, 4, 1, 0);
		writeEntry(out, IMAGE_WIDTH, 4, 1, fi.width);
		writeEntry(out, IMAGE_LENGTH, 4, 1, fi.height);
		if (fi.fileType==Constants.RGB||fi.fileType==Constants.RGB48) {
			writeEntry(out, BITS_PER_SAMPLE,  3, 3, tagDataOffset);
			tagDataOffset += BPS_DATA_SIZE;
		} else
			writeEntry(out,BITS_PER_SAMPLE,  3, 1, bitsPerSample);
		writeEntry(out, COMPRESSION,  3, 1, 1);	//No Compression
		writeEntry(out, PHOTO_INTERP, 3, 1, photoInterp);
		if (description!=null) {
			writeEntry(out, IMAGE_DESCRIPTION, 2, description.length, tagDataOffset);
			tagDataOffset += description.length;
		}
		writeEntry(out, STRIP_OFFSETS,    4, 1, imageOffset);
		writeEntry(out, SAMPLES_PER_PIXEL,3, 1, samplesPerPixel);
		writeEntry(out, ROWS_PER_STRIP,   3, 1, fi.height);
		writeEntry(out, STRIP_BYTE_COUNT, 4, 1, imageSize);
		if (fi.unit!=null && fi.pixelWidth!=0 && fi.pixelHeight!=0) {
			writeEntry(out, X_RESOLUTION, 5, 1, tagDataOffset);
			writeEntry(out, Y_RESOLUTION, 5, 1, tagDataOffset+8);
			tagDataOffset += SCALE_DATA_SIZE;
			int unit = 1;
			if (fi.unit.equals("inch"))
				unit = 2;
			else if (fi.unit.equals("cm"))
				unit = 3;
			writeEntry(out, RESOLUTION_UNIT, 3, 1, unit);
		}
		if (fi.fileType==Constants.GRAY32_FLOAT) {
			int format = FLOATING_POINT;
			writeEntry(out, SAMPLE_FORMAT, 3, 1, format);
		}
		if (colorMapSize>0) {
			writeEntry(out, COLOR_MAP, 3, MAP_SIZE, tagDataOffset);
			tagDataOffset += MAP_SIZE*2;
		}
		if (metaDataSize>0) {
			writeEntry(out, META_DATA_BYTE_COUNTS, 4, nMetaDataEntries, tagDataOffset);
			writeEntry(out, META_DATA, 1, metaDataSize, tagDataOffset+4*nMetaDataEntries);
			tagDataOffset += nMetaDataEntries*4 + metaDataSize;
		}
		writeInt(out, nextIFD);
	}
	
	/** Writes the 6 bytes of data required by RGB BitsPerSample tag. */
	void writeBitsPerPixel(OutputStream out) throws IOException {
		int bitsPerPixel = fi.fileType==Constants.RGB48?16:8;
		writeShort(out, bitsPerPixel);
		writeShort(out, bitsPerPixel);
		writeShort(out, bitsPerPixel);
	}

	/** Writes the 16 bytes of data required by the XResolution and YResolution tags. */
	void writeScale(OutputStream out) throws IOException {
		double xscale = 1.0/fi.pixelWidth;
		double yscale = 1.0/fi.pixelHeight;
		double scale = 1000000.0;
		if (xscale*scale>Integer.MAX_VALUE||yscale*scale>Integer.MAX_VALUE)
			scale = (int)(Integer.MAX_VALUE/Math.max(xscale,yscale));
		writeInt(out, (int)(xscale*scale));
		writeInt(out, (int)scale);
		writeInt(out, (int)(yscale*scale));
		writeInt(out, (int)scale);
	}

	/** Writes the variable length ImageDescription string. */
	void writeDescription(OutputStream out) throws IOException {
		out.write(description,0,description.length);
	}

	/** Writes color palette following the image. */
	void writeColorMap(OutputStream out) throws IOException {
		byte[] colorTable16 = new byte[MAP_SIZE*2];
		int j=littleEndian?1:0;
		for (int i=0; i<fi.lutSize; i++) {
			colorTable16[j] = fi.reds[i];
			colorTable16[512+j] = fi.greens[i];
			colorTable16[1024+j] = fi.blues[i];
			j += 2;
		}
		out.write(colorTable16);
	}
	
	/** Writes image metadata ("info" property, 
		stack slice labels, channel display ranges, luts, ROIs,
		overlays, properties and extra metadata). */
	void writeMetaData(OutputStream out) throws IOException {
	
		// write byte counts (META_DATA_BYTE_COUNTS tag)
		writeInt(out, 4+nMetaDataTypes*8); // header size	
		if (fi.info!=null && fi.info.length()>0)
			writeInt(out, fi.info.length()*2);
		for (int i=0; i<nSliceLabels; i++) {
			if (fi.sliceLabels[i]==null)
				writeInt(out, 0);
			else
				writeInt(out, fi.sliceLabels[i].length()*2);
		}
		if (fi.displayRanges!=null)
			writeInt(out, fi.displayRanges.length*8);
		if (fi.channelLuts!=null) {
			for (int i=0; i<fi.channelLuts.length; i++)
				writeInt(out, fi.channelLuts[i].length);
		}
		if (fi.plot!=null)
			writeInt(out, fi.plot.length);
		if (fi.roi!=null)
			writeInt(out, fi.roi.length);
		if (fi.overlay!=null) {
			for (int i=0; i<fi.overlay.length; i++)
				writeInt(out, fi.overlay[i].length);
		}
		if (fi.properties!=null) {
			for (int i=0; i<fi.properties.length; i++)
				writeInt(out, fi.properties[i].length()*2);
		}
		for (int i=0; i<extraMetaDataEntries; i++)
			writeInt(out, fi.metaData[i].length);	
		
		// write header (META_DATA tag header)
		writeInt(out, MAGIC_NUMBER); // "IJIJ"
		if (fi.info!=null) {
			writeInt(out, INFO); // type="info"
			writeInt(out, 1); // count
		}
		if (nSliceLabels>0) {
			writeInt(out, LABELS); // type="labl"
			writeInt(out, nSliceLabels); // count
		}
		if (fi.displayRanges!=null) {
			writeInt(out, RANGES); // type="rang"
			writeInt(out, 1); // count
		}
		if (fi.channelLuts!=null) {
			writeInt(out, LUTS); // type="luts"
			writeInt(out, fi.channelLuts.length); // count
		}
		if (fi.plot!=null) {
			writeInt(out, PLOT); // type="plot"
			writeInt(out, 1); // count
		}
		if (fi.roi!=null) {
			writeInt(out, ROI); // type="roi "
			writeInt(out, 1); // count
		}
		if (fi.overlay!=null) {
			writeInt(out, OVERLAY); // type="over"
			writeInt(out, fi.overlay.length); // count
		}
		if (fi.properties!=null) {
			writeInt(out, PROPERTIES); // type="prop"
			writeInt(out, fi.properties.length); // count
		}
		for (int i=0; i<extraMetaDataEntries; i++) {
			writeInt(out, fi.metaDataTypes[i]);
			writeInt(out, 1); // count
		}
		
		// write data (META_DATA tag body)
		if (fi.info!=null)
			writeChars(out, fi.info);
		for (int i=0; i<nSliceLabels; i++) {
			if (fi.sliceLabels[i]!=null)
				writeChars(out, fi.sliceLabels[i]);
		}
		if (fi.displayRanges!=null) {
			for (int i=0; i<fi.displayRanges.length; i++)
				writeDouble(out, fi.displayRanges[i]);
		}
		if (fi.channelLuts!=null) {
			for (int i=0; i<fi.channelLuts.length; i++)
				out.write(fi.channelLuts[i]);
		}
		if (fi.plot!=null)
			out.write(fi.plot);
		if (fi.roi!=null)
			out.write(fi.roi);
		if (fi.overlay!=null) {
			for (int i=0; i<fi.overlay.length; i++)
				out.write(fi.overlay[i]);
		}
		if (fi.properties!=null) {
			for (int i=0; i<fi.properties.length; i++)
				writeChars(out, fi.properties[i]);
		}
		for (int i=0; i<extraMetaDataEntries; i++)
			out.write(fi.metaData[i]); 					
	}

	/** Creates an optional image description string for saving calibration data.
		For stacks, also saves the stack size so ImageJ can open the stack without
		decoding an IFD for each slice.*/
	void makeDescriptionString() {
		if (fi.description!=null) {
			if (fi.description.charAt(fi.description.length()-1)!=(char)0)
				fi.description += " ";
			description = fi.description.getBytes();
			description[description.length-1] = (byte)0;
		} else
			description = null;
	}
		
	final void writeShort(OutputStream out, int v) throws IOException {
		if (littleEndian) {
       		out.write(v&255);
        	out.write((v>>>8)&255);
 		} else {
        	out.write((v>>>8)&255);
        	out.write(v&255);
        }
	}

	final void writeInt(OutputStream out, int v) throws IOException {
		if (littleEndian) {
        	out.write(v&255);
        	out.write((v>>>8)&255);
        	out.write((v>>>16)&255);
         	out.write((v>>>24)&255);
		} else {
        	out.write((v>>>24)&255);
        	out.write((v>>>16)&255);
        	out.write((v>>>8)&255);
        	out.write(v&255);
        }
	}

    final void writeLong(OutputStream out, long v) throws IOException {
    	if (littleEndian) {
			buffer[7] = (byte)(v>>>56);
			buffer[6] = (byte)(v>>>48);
			buffer[5] = (byte)(v>>>40);
			buffer[4] = (byte)(v>>>32);
			buffer[3] = (byte)(v>>>24);
			buffer[2] = (byte)(v>>>16);
			buffer[1] = (byte)(v>>> 8);
			buffer[0] = (byte)v;
			out.write(buffer, 0, 8);
        } else {
			buffer[0] = (byte)(v>>>56);
			buffer[1] = (byte)(v>>>48);
			buffer[2] = (byte)(v>>>40);
			buffer[3] = (byte)(v>>>32);
			buffer[4] = (byte)(v>>>24);
			buffer[5] = (byte)(v>>>16);
			buffer[6] = (byte)(v>>> 8);
			buffer[7] = (byte)v;
			out.write(buffer, 0, 8);
        }
     }

    final void writeDouble(OutputStream out, double v) throws IOException {
		writeLong(out, Double.doubleToLongBits(v));
    }
    
	final void writeChars(OutputStream out, String s) throws IOException {
        int len = s.length();
        if (littleEndian) {
			for (int i = 0 ; i < len ; i++) {
				int v = s.charAt(i);
				out.write(v&255); 
				out.write((v>>>8)&255); 
			}
        } else {
			for (int i = 0 ; i < len ; i++) {
				int v = s.charAt(i);
				out.write((v>>>8)&255); 
				out.write(v&255); 
			}
        }
    }
	
	
	
	
	
	
}
