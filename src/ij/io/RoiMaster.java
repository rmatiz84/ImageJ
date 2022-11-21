package ij.io;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import ij.ImagePlus;
import ij.gui.Arrow;
import ij.gui.EllipseRoi;
import ij.gui.ImageRoi;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.RotatedRectRoi;
import ij.gui.ShapeRoi;
import ij.gui.TextRoi;
import ij.process.FloatPolygon;

public class RoiMaster {
	
	
	static final int HEADER_SIZE = 64;
	static final int HEADER2_SIZE = 64;
	static final int VERSION = 228; // v1.52t (roi groups, scale stroke width)
	private String path;
	private OutputStream f;
	private final int polygon=0, rect=1, oval=2, line=3, freeline=4, polyline=5, noRoi=6, freehand=7, 
		traced=8, angle=9, point=10;
	private byte[] data;
	private String roiName;
	private int roiNameSize;
	private String roiProps;
	private int roiPropsSize;
	private int countersSize;
	private int[] counters;

	/** Creates an RoiEncoder using the specified OutputStream. */
	public RoiMaster(OutputStream f) {
		this.f = f;
	}
	
	/** Saves the specified ROI as a file, returning 'true' if successful. */
	public static boolean save(Roi roi, String path) {
		RoiMaster re = new RoiMaster(path);
		try {
			re.write(roi);
		} catch (IOException e) {
			return false;
		}
		return true;
	}

	/** Save the Roi to the file of stream. */
	public void write(Roi roi) throws IOException {
		if (f!=null) {
			write(roi, f);
		} else {
			f = new FileOutputStream(path);
			write(roi, f);
			f.close();
		}
	}
	
	/** Saves the specified ROI as a byte array. */
	public static byte[] saveAsByteArray(Roi roi) {
		if (roi==null) return null;
		byte[] bytes = null;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
			RoiMaster encoder = new RoiMaster(out);
			encoder.write(roi);
			out.close();
			bytes = out.toByteArray(); 
		} catch (IOException e) {
			return null;
		}
		return bytes;
	}

	void write(Roi roi, OutputStream f) throws IOException {
		Rectangle r = roi.getBounds();
		if (r.width>65535||r.height>65535||r.x>65535||r.y>65535)
			roi.enableSubPixelResolution();
		int roiType = roi.getType();
		int type = rect;
		int options = 0;
		if (roi.getScaleStrokeWidth())
			options |= SCALE_STROKE_WIDTH;
		roiName = roi.getName();
		if (roiName!=null)
			roiNameSize = roiName.length()*2;
		else
			roiNameSize = 0;
		
		roiProps = roi.getProperties();
		if (roiProps!=null)
			roiPropsSize = roiProps.length()*2;
		else
			roiPropsSize = 0;

		switch (roiType) {
			case Roi.POLYGON: type=polygon; break;
			case Roi.FREEROI: type=freehand; break;
			case Roi.TRACED_ROI: type=traced; break;
			case Roi.OVAL: type=oval; break;
			case Roi.LINE: type=line; break;
			case Roi.POLYLINE: type=polyline; break;
			case Roi.FREELINE: type=freeline; break;
			case Roi.ANGLE: type=angle; break;
			case Roi.COMPOSITE: type=rect; break; // shape array size (36-39) will be >0 to indicate composite type
			case Roi.POINT: type=point; break;
			default: type = rect; break;
		}
		
		if (roiType==Roi.COMPOSITE) {
			saveShapeRoi(roi, type, f, options);
			return;
		}

		int n=0;
		int[] x=null, y=null;
		float[] xf=null, yf=null;
		int floatSize = 0;
		if (roi instanceof PolygonRoi) {
			PolygonRoi proi = (PolygonRoi)roi;
			Polygon p = proi.getNonSplineCoordinates();
			n = p.npoints;
			x = p.xpoints;
			y = p.ypoints;
			if (roi.subPixelResolution()) {
				FloatPolygon fp = null;
				if (proi.isSplineFit())
					fp = proi.getNonSplineFloatPolygon();
				else
					fp = roi.getFloatPolygon();
				if (n==fp.npoints) {
					options |= SUB_PIXEL_RESOLUTION;
					if (roi.getDrawOffset())
						options |= DRAW_OFFSET;
					xf = fp.xpoints;
					yf = fp.ypoints;
					floatSize = n*8;
				}
			}
		}
		
		countersSize = 0;
		if (roi instanceof PointRoi) {
			counters = ((PointRoi)roi).getCounters();
			if (counters!=null && counters.length>=n)
				countersSize = n*4;
		}
		
		data = new byte[HEADER_SIZE+HEADER2_SIZE+n*4+floatSize+roiNameSize+roiPropsSize+countersSize];
		data[0]=73; data[1]=111; data[2]=117; data[3]=116; // "Iout"
		putShort(VERSION_OFFSET, VERSION);
		data[TYPE] = (byte)type;
		putShort(TOP, r.y);
		putShort(LEFT, r.x);
		putShort(BOTTOM, r.y+r.height);
		putShort(RIGHT, r.x+r.width);	
		if (roi.subPixelResolution() && (type==rect||type==oval)) {
			FloatPolygon p = null;
			if (roi instanceof OvalRoi)
				p = ((OvalRoi)roi).getFloatPolygon4();
			else {
				int d = roi.getCornerDiameter();
				if (d>0) {
					roi.setCornerDiameter(0);
					p = roi.getFloatPolygon();
					roi.setCornerDiameter(d);
				} else
					p = roi.getFloatPolygon();
			}
			if (p.npoints==4) {
				putFloat(XD, p.xpoints[0]);
				putFloat(YD, p.ypoints[0]);
				putFloat(WIDTHD, p.xpoints[1]-p.xpoints[0]);
				putFloat(HEIGHTD, p.ypoints[2]-p.ypoints[1]);	
				options |= SUB_PIXEL_RESOLUTION;
				putShort(OPTIONS, options);
			}
		}
		if (n>65535 && type!=point) {
			if (type==polygon || type==freehand || type==traced) {
				String name = roi.getName();
				roi = new ShapeRoi(roi);
				if (name!=null) roi.setName(name);
				saveShapeRoi(roi, rect, f, options);
				return;
			}
			ij.IJ.beep();
			ij.IJ.log("Non-polygonal selections with more than 65k points cannot be saved.");
			n = 65535;
		}
		if (type==point && n>65535)
			putInt(SIZE, n);
		else 
			putShort(N_COORDINATES, n);
		putInt(POSITION, roi.getPosition());
		
		if (type==rect) {
			int arcSize = roi.getCornerDiameter();
			if (arcSize>0)
				putShort(ROUNDED_RECT_ARC_SIZE, arcSize);
		}
		
		if (roi instanceof Line) {
			Line line = (Line)roi;
			putFloat(X1, (float)line.x1d);
			putFloat(Y1, (float)line.y1d);
			putFloat(X2, (float)line.x2d);
			putFloat(Y2, (float)line.y2d);
			if (roi instanceof Arrow) {
				putShort(SUBTYPE, ARROW);
				if (((Arrow)roi).getDoubleHeaded())
					options |= DOUBLE_HEADED;
				if (((Arrow)roi).getOutline())
					options |= OUTLINE;
				putShort(OPTIONS, options);
				putByte(ARROW_STYLE, ((Arrow)roi).getStyle());
				putByte(ARROW_HEAD_SIZE, (int)((Arrow)roi).getHeadSize());
			} else {
				if (roi.getDrawOffset())
					options |= SUB_PIXEL_RESOLUTION+DRAW_OFFSET;
			}
		}
		
		if (roi instanceof PointRoi) {
			PointRoi point = (PointRoi)roi;
			putByte(POINT_TYPE, point.getPointType());
			putShort(STROKE_WIDTH, point.getSize());
			if (point.getShowLabels())
				options |= SHOW_LABELS;
			if (point.promptBeforeDeleting())
				options |= PROMPT_BEFORE_DELETING;
		}

		if (roi instanceof RotatedRectRoi || roi instanceof EllipseRoi) {
			double[] p = null;
			if (roi instanceof RotatedRectRoi) {
				putShort(SUBTYPE, ROTATED_RECT);
				p = ((RotatedRectRoi)roi).getParams();
			} else {
				putShort(SUBTYPE, ELLIPSE);
				p = ((EllipseRoi)roi).getParams();
			}
			putFloat(X1, (float)p[0]);
			putFloat(Y1, (float)p[1]);
			putFloat(X2, (float)p[2]);
			putFloat(Y2, (float)p[3]);
			putFloat(FLOAT_PARAM, (float)p[4]);
		}
				
		// save stroke width, stroke color and fill color (1.43i or later)
		if (VERSION>=218) {
			saveStrokeWidthAndColor(roi);
			if ((roi instanceof PolygonRoi) && ((PolygonRoi)roi).isSplineFit()) {
				options |= SPLINE_FIT;
				putShort(OPTIONS, options);
			}
		}
		
		if (n==0 && roi instanceof TextRoi)
			saveTextRoi((TextRoi)roi);
		else if (n==0 && roi instanceof ImageRoi)
			options = saveImageRoi((ImageRoi)roi, options);
		else
			putHeader2(roi, HEADER_SIZE+n*4+floatSize);
			
		if (n>0) {
			int base1 = 64;
			int base2 = base1+2*n;
			for (int i=0; i<n; i++) {
				putShort(base1+i*2, x[i]);
				putShort(base2+i*2, y[i]);
			}
			if (xf!=null) {
				base1 = 64+4*n;
				base2 = base1+4*n;
				for (int i=0; i<n; i++) {
					putFloat(base1+i*4, xf[i]);
					putFloat(base2+i*4, yf[i]);
				}
			}
		}
		
		saveOverlayOptions(roi, options);
		f.write(data);
	}

	void saveStrokeWidthAndColor(Roi roi) {
		BasicStroke stroke = roi.getStroke();
		if (stroke!=null)
			putShort(STROKE_WIDTH, (int)stroke.getLineWidth());
		Color strokeColor = roi.getStrokeColor();
		if (strokeColor!=null)
			putInt(STROKE_COLOR, strokeColor.getRGB());
		Color fillColor = roi.getFillColor();
		if (fillColor!=null)
			putInt(FILL_COLOR, fillColor.getRGB());
	}

	void saveShapeRoi(Roi roi, int type, OutputStream f, int options) throws IOException {
		float[] shapeArray = ((ShapeRoi)roi).getShapeAsArray();
		if (shapeArray==null) return;
		BufferedOutputStream bout = new BufferedOutputStream(f);
		Rectangle r = roi.getBounds();
		data  = new byte[HEADER_SIZE+HEADER2_SIZE+shapeArray.length*4+roiNameSize+roiPropsSize];
		data[0]=73; data[1]=111; data[2]=117; data[3]=116; // "Iout"
		
		putShort(VERSION_OFFSET, VERSION);
		data[TYPE] = (byte)type;
		putShort(TOP, r.y);
		putShort(LEFT, r.x);
		putShort(BOTTOM, r.y+r.height);
		putShort(RIGHT, r.x+r.width);	
		putInt(POSITION, roi.getPosition());
		//putShort(16, n);
		putInt(36, shapeArray.length); // non-zero segment count indicate composite type
		if (VERSION>=218) saveStrokeWidthAndColor(roi);
		saveOverlayOptions(roi, options);

		// handle the actual data: data are stored segment-wise, i.e.,
		// the type of the segment followed by 0-6 control point coordinates.
		int base = 64;
		for (int i=0; i<shapeArray.length; i++) {
			putFloat(base, shapeArray[i]);
			base += 4;
		}
		int hdr2Offset = HEADER_SIZE+shapeArray.length*4;
		//ij.IJ.log("saveShapeRoi: "+HEADER_SIZE+"  "+shapeArray.length);
		putHeader2(roi, hdr2Offset);
		bout.write(data,0,data.length);
		bout.flush();
	}
	
	void saveOverlayOptions(Roi roi, int options) {
		Overlay proto = roi.getPrototypeOverlay();
		if (proto.getDrawLabels())
			options |= OVERLAY_LABELS;
		if (proto.getDrawNames())
			options |= OVERLAY_NAMES;
		if (proto.getDrawBackgrounds())
			options |= OVERLAY_BACKGROUNDS;
		Font font = proto.getLabelFont();
		if (font!=null && font.getStyle()==Font.BOLD)
			options |= OVERLAY_BOLD;
		if (proto.scalableLabels())
			options |= SCALE_LABELS;
		putShort(OPTIONS, options);
	}
	
	void saveTextRoi(TextRoi roi) {
		Font font = roi.getCurrentFont();
		String fontName = font.getName();
		int size = font.getSize();
		int drawStringMode = roi.getDrawStringMode()?1024:0;
		int style = font.getStyle() + roi.getJustification()*256+drawStringMode;
		String text = roi.getText();
		float angle = (float)roi.getAngle();
		int angleLength = 4;
		int fontNameLength = fontName.length();
		int textLength = text.length();
		int textRoiDataLength = 16+fontNameLength*2+textLength*2 + angleLength;
		byte[] data2 = new byte[HEADER_SIZE+HEADER2_SIZE+textRoiDataLength+roiNameSize+roiPropsSize];
		System.arraycopy(data, 0, data2, 0, HEADER_SIZE);
		data = data2;
		putShort(SUBTYPE, TEXT);
		putInt(HEADER_SIZE, size);
		putInt(HEADER_SIZE+4, style);
		putInt(HEADER_SIZE+8, fontNameLength);
		putInt(HEADER_SIZE+12, textLength);
		for (int i=0; i<fontNameLength; i++)
			putShort(HEADER_SIZE+16+i*2, fontName.charAt(i));
		for (int i=0; i<textLength; i++)
			putShort(HEADER_SIZE+16+fontNameLength*2+i*2, text.charAt(i));
		int hdr2Offset = HEADER_SIZE+textRoiDataLength;
		//ij.IJ.log("saveTextRoi: "+HEADER_SIZE+"  "+textRoiDataLength+"  "+fontNameLength+"  "+textLength);
		putFloat(hdr2Offset-angleLength, angle);
		putHeader2(roi, hdr2Offset);
	}
	
	private int saveImageRoi(ImageRoi roi, int options) {
		byte[] bytes = roi.getSerializedImage();
		int imageSize = bytes.length;
		byte[] data2 = new byte[HEADER_SIZE+HEADER2_SIZE+imageSize+roiNameSize+roiPropsSize];
		System.arraycopy(data, 0, data2, 0, HEADER_SIZE);
		data = data2;
		putShort(SUBTYPE, IMAGE);
		for (int i=0; i<imageSize; i++)
			putByte(HEADER_SIZE+i, bytes[i]&255);
		int hdr2Offset = HEADER_SIZE+imageSize;
		double opacity = roi.getOpacity();
		putByte(hdr2Offset+IMAGE_OPACITY, (int)(opacity*255.0));
		putInt(hdr2Offset+IMAGE_SIZE, imageSize);
		if (roi.getZeroTransparent())
			options |= ZERO_TRANSPARENT;
		putHeader2(roi, hdr2Offset);
		return options;
	}

	void putHeader2(Roi roi, int hdr2Offset) {
		//ij.IJ.log("putHeader2: "+hdr2Offset+" "+roiNameSize+"  "+roiName);
		putInt(HEADER2_OFFSET, hdr2Offset);
		putInt(hdr2Offset+C_POSITION, roi.getCPosition());
		putInt(hdr2Offset+Z_POSITION, roi.hasHyperStackPosition()?roi.getZPosition():0);
		putInt(hdr2Offset+T_POSITION, roi.getTPosition());
		Overlay proto = roi.getPrototypeOverlay();
		Color overlayLabelColor = proto.getLabelColor();
		if (overlayLabelColor!=null)
			putInt(hdr2Offset+OVERLAY_LABEL_COLOR, overlayLabelColor.getRGB());
		Font font = proto.getLabelFont();
		if (font!=null)
			putShort(hdr2Offset+OVERLAY_FONT_SIZE, font.getSize());
		if (roiNameSize>0)
			putName(roi, hdr2Offset);
		double strokeWidth = roi.getStrokeWidth();
		if (roi.getStroke()==null)
			strokeWidth = 0.0;
		putFloat(hdr2Offset+FLOAT_STROKE_WIDTH, (float)strokeWidth);
		if (roiPropsSize>0)
			putProps(roi, hdr2Offset);
		if (countersSize>0)
			putPointCounters(roi, hdr2Offset);
		putByte(hdr2Offset+GROUP, roi.getGroup());
	}

	void putName(Roi roi, int hdr2Offset) {
		int offset = hdr2Offset+HEADER2_SIZE;
		int nameLength = roiNameSize/2;
		putInt(hdr2Offset+NAME_OFFSET, offset);
		putInt(hdr2Offset+NAME_LENGTH, nameLength);
		for (int i=0; i<nameLength; i++)
			putShort(offset+i*2, roiName.charAt(i));
	}

	void putProps(Roi roi, int hdr2Offset) {
		int offset = hdr2Offset+HEADER2_SIZE+roiNameSize;
		int roiPropsLength = roiPropsSize/2;
		putInt(hdr2Offset+ROI_PROPS_OFFSET, offset);
		putInt(hdr2Offset+ROI_PROPS_LENGTH, roiPropsLength);
		for (int i=0; i<roiPropsLength; i++)
			putShort(offset+i*2, roiProps.charAt(i));
	}

	void putPointCounters(Roi roi, int hdr2Offset) {
		int offset = hdr2Offset+HEADER2_SIZE+roiNameSize+roiPropsSize;
		putInt(hdr2Offset+COUNTERS_OFFSET, offset);
		for (int i=0; i<countersSize/4; i++)
			putInt(offset+i*4, counters[i]);
		countersSize = 0;
	}

    void putByte(int base, int v) {
		data[base] = (byte)v;
    }

    void putShort(int base, int v) {
		data[base] = (byte)(v>>>8);
		data[base+1] = (byte)v;
    }

	void putFloat(int base, float v) {
		int tmp = Float.floatToIntBits(v);
		data[base]   = (byte)(tmp>>24);
		data[base+1] = (byte)(tmp>>16);
		data[base+2] = (byte)(tmp>>8);
		data[base+3] = (byte)tmp;
	}

	void putInt(int base, int i) {
		data[base]   = (byte)(i>>24);
		data[base+1] = (byte)(i>>16);
		data[base+2] = (byte)(i>>8);
		data[base+3] = (byte)i;
	}
	
	
	
	// offsets
	public static final int VERSION_OFFSET = 4;
	public static final int TYPE = 6;
	public static final int TOP = 8;
	public static final int LEFT = 10;
	public static final int BOTTOM = 12;
	public static final int RIGHT = 14;
	public static final int N_COORDINATES = 16;
	public static final int X1 = 18;
	public static final int Y1 = 22;
	public static final int X2 = 26;
	public static final int Y2 = 30;
	public static final int XD = 18;
	public static final int YD = 22;
	public static final int WIDTHD = 26;
	public static final int HEIGHTD = 30;
	public static final int SIZE = 18;
	public static final int STROKE_WIDTH = 34;
	public static final int SHAPE_ROI_SIZE = 36;
	public static final int STROKE_COLOR = 40;
	public static final int FILL_COLOR = 44;
	public static final int SUBTYPE = 48;
	public static final int OPTIONS = 50;
	public static final int ARROW_STYLE = 52;
	public static final int FLOAT_PARAM = 52; //ellipse ratio or rotated rect width
	public static final int POINT_TYPE= 52;
	public static final int ARROW_HEAD_SIZE = 53;
	public static final int ROUNDED_RECT_ARC_SIZE = 54;
	public static final int POSITION = 56;
	public static final int HEADER2_OFFSET = 60;
	public static final int COORDINATES = 64;
	// header2 offsets
	public static final int C_POSITION = 4;
	public static final int Z_POSITION = 8;
	public static final int T_POSITION = 12;
	public static final int NAME_OFFSET = 16;
	public static final int NAME_LENGTH = 20;
	public static final int OVERLAY_LABEL_COLOR = 24;
	public static final int OVERLAY_FONT_SIZE = 28; //short
	public static final int GROUP = 30;  //byte
	public static final int IMAGE_OPACITY = 31;  //byte
	public static final int IMAGE_SIZE = 32;  //int
	public static final int FLOAT_STROKE_WIDTH = 36;  //float
	public static final int ROI_PROPS_OFFSET = 40;
	public static final int ROI_PROPS_LENGTH = 44;
	public static final int COUNTERS_OFFSET = 48;

	// subtypes
	public static final int TEXT = 1;
	public static final int ARROW = 2;
	public static final int ELLIPSE = 3;
	public static final int IMAGE = 4;
	public static final int ROTATED_RECT = 5;
	
	// options
	public static final int SPLINE_FIT = 1;
	public static final int DOUBLE_HEADED = 2;
	public static final int OUTLINE = 4;
	public static final int OVERLAY_LABELS = 8;
	public static final int OVERLAY_NAMES = 16;
	public static final int OVERLAY_BACKGROUNDS = 32;
	public static final int OVERLAY_BOLD = 64;
	public static final int SUB_PIXEL_RESOLUTION = 128;
	public static final int DRAW_OFFSET = 256;
	public static final int ZERO_TRANSPARENT = 512;
	public static final int SHOW_LABELS = 1024;
	public static final int SCALE_LABELS = 2048;
	public static final int PROMPT_BEFORE_DELETING = 4096; //points
	public static final int SCALE_STROKE_WIDTH = 8192;
	
	private InputStream is;
	private String name;
	private int size;

	/** Constructs an RoiDecoder using a file path. */
	public RoiMaster(String path) {
		this.path = path;
	}

	/** Constructs an RoiDecoder using a byte array. */
	public RoiMaster(byte[] bytes, String name) {
		is = new ByteArrayInputStream(bytes);	
		this.name = name;
		this.size = bytes.length;
	}

	/** Opens the Roi at the specified path. Returns null if there is an error. */
	public static Roi open(String path) {
		Roi roi = null;
		RoiMaster rd = new RoiMaster(path);
		try {
			roi = rd.getRoi();
		} catch (IOException e) { }
		return roi;
	}

	/** Returns the ROI. */
	public Roi getRoi() throws IOException {
		if (path!=null) {
			File f = new File(path);
			size = (int)f.length();
			if (!path.endsWith(".roi") && size>5242880)
				throw new IOException("This is not an ROI or file size>5MB)");
			name = f.getName();
			is = new FileInputStream(path);
		}
		data = new byte[size];

		int total = 0;
		while (total<size)
			total += is.read(data, total, size-total);
		is.close();
		if (getByte(0)!=73 || getByte(1)!=111)  //"Iout"
			throw new IOException("This is not an ImageJ ROI");
		int version = getShort(VERSION_OFFSET);
		int type = getByte(TYPE);
		int subtype = getShort(SUBTYPE);
		int top= getShort(TOP);
		int left = getShort(LEFT);
		int bottom = getShort(BOTTOM);
		int right = getShort(RIGHT);
		int width = right-left;
		int height = bottom-top;
		int n = getUnsignedShort(N_COORDINATES);
		if (n==0)
			n = getInt(SIZE);
		int options = getShort(OPTIONS);
		int position = getInt(POSITION);
		int hdr2Offset = getInt(HEADER2_OFFSET);
		int channel=0, slice=0, frame=0;
		int overlayLabelColor=0;
		int overlayFontSize=0;
		int group=0;
		int imageOpacity=0;
		int imageSize=0;
		boolean subPixelResolution = (options&SUB_PIXEL_RESOLUTION)!=0 &&  version>=222;
		boolean drawOffset = subPixelResolution && (options&DRAW_OFFSET)!=0;
		boolean scaleStrokeWidth = true;
		if (version>=228)
			scaleStrokeWidth = (options&SCALE_STROKE_WIDTH)!=0;
		
		boolean subPixelRect = version>=223 && subPixelResolution && (type==rect||type==oval);
		double xd=0.0, yd=0.0, widthd=0.0, heightd=0.0;
		if (subPixelRect) {
			xd = getFloat(XD);
			yd = getFloat(YD);
			widthd = getFloat(WIDTHD);
			heightd = getFloat(HEIGHTD);
		}
		
		if (hdr2Offset>0 && hdr2Offset+IMAGE_SIZE+4<=size) {
			channel = getInt(hdr2Offset+C_POSITION);
			slice = getInt(hdr2Offset+Z_POSITION);
			frame = getInt(hdr2Offset+T_POSITION);
			overlayLabelColor = getInt(hdr2Offset+OVERLAY_LABEL_COLOR);
			overlayFontSize = getShort(hdr2Offset+OVERLAY_FONT_SIZE);
			imageOpacity = getByte(hdr2Offset+IMAGE_OPACITY);
			imageSize = getInt(hdr2Offset+IMAGE_SIZE);
			group = getByte(hdr2Offset+GROUP);
		}
		
		if (name!=null && name.endsWith(".roi"))
			name = name.substring(0, name.length()-4);
		boolean isComposite = getInt(SHAPE_ROI_SIZE)>0;
		
		Roi roi = null;
		if (isComposite) {
			roi = getShapeRoi();
			if (version>=218)
				getStrokeWidthAndColor(roi, hdr2Offset, scaleStrokeWidth);
			roi.setPosition(position);
			if (channel>0 || slice>0 || frame>0)
				roi.setPosition(channel, slice, frame);
			decodeOverlayOptions(roi, version, options, overlayLabelColor, overlayFontSize);
			if (version>=224) {
				String props = getRoiProps();
				if (props!=null)
					roi.setProperties(props);
			}
			if (version>=228 && group>0)
				roi.setGroup(group);
			return roi;
		}

		switch (type) {
			case rect:
				if (subPixelRect)
					roi = new Roi(xd, yd, widthd, heightd);
				else
					roi = new Roi(left, top, width, height);
				int arcSize = getShort(ROUNDED_RECT_ARC_SIZE);
				if (arcSize>0)
					roi.setCornerDiameter(arcSize);
				break;
			case oval:
				if (subPixelRect)
					roi = new OvalRoi(xd, yd, widthd, heightd);
				else
					roi = new OvalRoi(left, top, width, height);
				break;
			case line:
				double x1 = getFloat(X1);		
				double y1 = getFloat(Y1);		
				double x2 = getFloat(X2);		
				double y2 = getFloat(Y2);
				if (subtype==ARROW) {
					roi = new Arrow(x1, y1, x2, y2);		
					((Arrow)roi).setDoubleHeaded((options&DOUBLE_HEADED)!=0);
					((Arrow)roi).setOutline((options&OUTLINE)!=0);
					int style = getByte(ARROW_STYLE);
					if (style>=Arrow.FILLED && style<=Arrow.BAR)
						((Arrow)roi).setStyle(style);
					int headSize = getByte(ARROW_HEAD_SIZE);
					if (headSize>=0 && style<=30)
						((Arrow)roi).setHeadSize(headSize);
				} else {
					roi = new Line(x1, y1, x2, y2);
					roi.setDrawOffset(drawOffset);
				}
				break;
			case polygon: case freehand: case traced: case polyline: case freeline: case angle: case point:
					//IJ.log("type: "+type);
					//IJ.log("n: "+n);
					//IJ.log("rect: "+left+","+top+" "+width+" "+height);
					if (n==0 || n<0) break;
					int[] x = new int[n];
					int[] y = new int[n];
					float[] xf = null;
					float[] yf = null;
					int base1 = COORDINATES;
					int base2 = base1+2*n;
					int xtmp, ytmp;
					for (int i=0; i<n; i++) {
						xtmp = getShort(base1+i*2);
						if (xtmp<0) xtmp = 0;
						ytmp = getShort(base2+i*2);
						if (ytmp<0) ytmp = 0;
						x[i] = left+xtmp;
						y[i] = top+ytmp;
					}
					if (subPixelResolution) {
						xf = new float[n];
						yf = new float[n];
						base1 = COORDINATES+4*n;
						base2 = base1+4*n;
						for (int i=0; i<n; i++) {
							xf[i] = getFloat(base1+i*4);
							yf[i] = getFloat(base2+i*4);
						}
					}
					if (type==point) {
						if (subPixelResolution)
							roi = new PointRoi(xf, yf, n);
						else
							roi = new PointRoi(x, y, n);
						if (version>=226) {
							((PointRoi)roi).setPointType(getByte(POINT_TYPE));
							((PointRoi)roi).setSize(getShort(STROKE_WIDTH));
						}
						if ((options&SHOW_LABELS)!=0 && !ij.Prefs.noPointLabels)
							((PointRoi)roi).setShowLabels(true);
						if ((options&PROMPT_BEFORE_DELETING)!=0)
							((PointRoi)roi).promptBeforeDeleting(true);
						break;
					}
					int roiType;
					if (type==polygon)
						roiType = Roi.POLYGON;
					else if (type==freehand) {
						roiType = Roi.FREEROI;
						if (subtype==ELLIPSE || subtype==ROTATED_RECT) {
							double ex1 = getFloat(X1);		
							double ey1 = getFloat(Y1);		
							double ex2 = getFloat(X2);		
							double ey2 = getFloat(Y2);
							double param = getFloat(FLOAT_PARAM);
							if (subtype==ROTATED_RECT)
								roi = new RotatedRectRoi(ex1,ey1,ex2,ey2,param);
							else
								roi = new EllipseRoi(ex1,ey1,ex2,ey2,param);
							break;
						}
					} else if (type==traced)
						roiType = Roi.TRACED_ROI;
					else if (type==polyline)
						roiType = Roi.POLYLINE;
					else if (type==freeline)
						roiType = Roi.FREELINE;
					else if (type==angle)
						roiType = Roi.ANGLE;
					else
						roiType = Roi.FREEROI;
					if (subPixelResolution) {
						roi = new PolygonRoi(xf, yf, n, roiType);
						roi.setDrawOffset(drawOffset);
					} else
						roi = new PolygonRoi(x, y, n, roiType);
					break;
			default:
				throw new IOException("Unrecognized ROI type: "+type);
		}
		if (roi==null)
			return null;
		roi.setName(getRoiName());
		
		// read stroke width, stroke color and fill color (1.43i or later)
		if (version>=218) {
			getStrokeWidthAndColor(roi, hdr2Offset, scaleStrokeWidth);
			if (type==point)
				roi.setStrokeWidth(0);
			boolean splineFit = (options&SPLINE_FIT)!=0;
			if (splineFit && roi instanceof PolygonRoi)
				((PolygonRoi)roi).fitSpline();
		}
		
		if (version>=218 && subtype==TEXT)
			roi = getTextRoi(roi, version);

		if (version>=221 && subtype==IMAGE)
			roi = getImageRoi(roi, imageOpacity, imageSize, options);

		if (version>=224) {
			String props = getRoiProps();
			if (props!=null)
				roi.setProperties(props);
		}

		if (version>=227) {
			int[] counters = getPointCounters(n);
			if (counters!=null && (roi instanceof PointRoi))
				((PointRoi)roi).setCounters(counters);
		}
		
		// set group (1.52t or later)
		if (version>=228 && group>0)
			roi.setGroup(group);

		roi.setPosition(position);
		if (channel>0 || slice>0 || frame>0)
			roi.setPosition(channel, slice, frame);
		decodeOverlayOptions(roi, version, options, overlayLabelColor, overlayFontSize);
		return roi;
	}
	
	void decodeOverlayOptions(Roi roi, int version, int options, int color, int fontSize) {
		Overlay proto = new Overlay();
		proto.drawLabels((options&OVERLAY_LABELS)!=0);
		proto.drawNames((options&OVERLAY_NAMES)!=0);
		proto.drawBackgrounds((options&OVERLAY_BACKGROUNDS)!=0);
		if (version>=220 && color!=0)
			proto.setLabelColor(new Color(color));
		boolean bold = (options&OVERLAY_BOLD)!=0;
		boolean scalable = (options&SCALE_LABELS)!=0;
		if (fontSize>0 || bold || scalable) {
			proto.setLabelFont(new Font("SansSerif", bold?Font.BOLD:Font.PLAIN, fontSize), scalable);
		}
		roi.setPrototypeOverlay(proto);
	}

	void getStrokeWidthAndColor(Roi roi, int hdr2Offset, boolean scaleStrokeWidth) {
		double strokeWidth = getShort(STROKE_WIDTH);
		if (hdr2Offset>0) {
			double strokeWidthD = getFloat(hdr2Offset+FLOAT_STROKE_WIDTH);
			if (strokeWidthD>0.0)
				strokeWidth = strokeWidthD;
		}
		if (strokeWidth>0.0) {
			if (scaleStrokeWidth)
				roi.setStrokeWidth(strokeWidth);
			else
				roi.setUnscalableStrokeWidth(strokeWidth);
		}
		int strokeColor = getInt(STROKE_COLOR);
		if (strokeColor!=0) {
			int alpha = (strokeColor>>24)&0xff;
			roi.setStrokeColor(new Color(strokeColor, alpha!=255));
		}
		int fillColor = getInt(FILL_COLOR);
		if (fillColor!=0) {
			int alpha = (fillColor>>24)&0xff;
			roi.setFillColor(new Color(fillColor, alpha!=255));
		}
	}

	public Roi getShapeRoi() throws IOException {
		int type = getByte(TYPE);
		if (type!=rect)
			throw new IllegalArgumentException("Invalid composite ROI type");
		int top= getShort(TOP);
		int left = getShort(LEFT);
		int bottom = getShort(BOTTOM);
		int right = getShort(RIGHT);
		int width = right-left;
		int height = bottom-top;
		int n = getInt(SHAPE_ROI_SIZE);

		ShapeRoi roi = null;
		float[] shapeArray = new float[n];
		int base = COORDINATES;
		for(int i=0; i<n; i++) {
			shapeArray[i] = getFloat(base);
			base += 4;
		}
		roi = new ShapeRoi(shapeArray);
		roi.setName(getRoiName());
		return roi;
	}
	
	Roi getTextRoi(Roi roi, int version) {
		Rectangle r = roi.getBounds();
		int hdrSize = HEADER_SIZE;
		int size = getInt(hdrSize);
		int styleAndJustification = getInt(hdrSize+4);
		int style = styleAndJustification&255;
		int justification = (styleAndJustification>>8) & 3;
		boolean drawStringMode = (styleAndJustification&1024)!=0;
		int nameLength = getInt(hdrSize+8);
		int textLength = getInt(hdrSize+12);
		char[] name = new char[nameLength];
		char[] text = new char[textLength];
		for (int i=0; i<nameLength; i++)
			name[i] = (char)getShort(hdrSize+16+i*2);
		for (int i=0; i<textLength; i++)
			text[i] = (char)getShort(hdrSize+16+nameLength*2+i*2);
		double angle = version>=225?getFloat(hdrSize+16+nameLength*2+textLength*2):0f;
		Font font = new Font(new String(name), style, size);
		TextRoi roi2 = null;
		if (roi.subPixelResolution()) {
			Rectangle2D fb = roi.getFloatBounds();
			roi2 = new TextRoi(fb.getX(), fb.getY(), fb.getWidth(), fb.getHeight(), new String(text), font);
		} else
			roi2 = new TextRoi(r.x, r.y, r.width, r.height, new String(text), font);
		roi2.setStrokeColor(roi.getStrokeColor());
		roi2.setFillColor(roi.getFillColor());
		roi2.setName(getRoiName());
		roi2.setJustification(justification);
		roi2.setDrawStringMode(drawStringMode);
		roi2.setAngle(angle);
		return roi2;
	}
	
	Roi getImageRoi(Roi roi, int opacity, int size, int options) {
		if (size<=0)
			return roi;
		Rectangle r = roi.getBounds();
		byte[] bytes = new byte[size];
		for (int i=0; i<size; i++)
			bytes[i] = (byte)getByte(COORDINATES+i);
		ImagePlus imp = new Opener().deserialize(bytes);
		ImageRoi roi2 = new ImageRoi(r.x, r.y, imp.getProcessor());
		roi2.setOpacity(opacity/255.0);
		if ((options&ZERO_TRANSPARENT)!=0)
			roi2.setZeroTransparent(true);
		return roi2;
	}

	String getRoiName() {
		String fileName = name;
		int hdr2Offset = getInt(HEADER2_OFFSET);
		if (hdr2Offset==0)
			return fileName;
		int offset = getInt(hdr2Offset+NAME_OFFSET);
		int length = getInt(hdr2Offset+NAME_LENGTH);
		if (offset==0 || length==0)
			return fileName;
		if (offset+length*2>size)
			return fileName;
		char[] name = new char[length];
		for (int i=0; i<length; i++)
			name[i] = (char)getShort(offset+i*2);
		return new String(name);
	}
	
	String getRoiProps() {
		int hdr2Offset = getInt(HEADER2_OFFSET);
		if (hdr2Offset==0)
			return null;
		int offset = getInt(hdr2Offset+ROI_PROPS_OFFSET);
		int length = getInt(hdr2Offset+ROI_PROPS_LENGTH);
		if (offset==0 || length==0)
			return null;
		if (offset+length*2>size)
			return null;
		char[] props = new char[length];
		for (int i=0; i<length; i++)
			props[i] = (char)getShort(offset+i*2);
		return new String(props);
	}
	
	int[] getPointCounters(int n) {
		int hdr2Offset = getInt(HEADER2_OFFSET);
		if (hdr2Offset==0)
			return null;
		int offset = getInt(hdr2Offset+COUNTERS_OFFSET);
		if (offset==0)
			return null;
		if (offset+n*4>data.length)
			return null;
		int[] counters = new int[n];
		for (int i=0; i<n; i++)
			counters[i] = getInt(offset+i*4);
		return counters;
	}


	int getByte(int base) {
		return data[base]&255;
	}

	int getShort(int base) {
		int b0 = data[base]&255;
		int b1 = data[base+1]&255;
		int n = (short)((b0<<8) + b1);
		if (n<-5000)
			n = (b0<<8) + b1; // assume n>32767 and unsigned
		return n;		
	}
	
	int getUnsignedShort(int base) {
		int b0 = data[base]&255;
		int b1 = data[base+1]&255;
		return (b0<<8) + b1;	
	}

	int getInt(int base) {
		int b0 = data[base]&255;
		int b1 = data[base+1]&255;
		int b2 = data[base+2]&255;
		int b3 = data[base+3]&255;
		return ((b0<<24) + (b1<<16) + (b2<<8) + b3);
	}

	float getFloat(int base) {
		return Float.intBitsToFloat(getInt(base));
	}
	
	/** Opens an ROI from a byte array. */
	public static Roi openFromByteArray(byte[] bytes) {
		Roi roi = null;
		if (bytes==null || bytes.length==0)
			return roi;
		try {
			RoiMaster decoder = new RoiMaster(bytes, null);
			roi = decoder.getRoi();
		} catch (IOException e) {
			return null;
		}
		return roi;
	}


}
