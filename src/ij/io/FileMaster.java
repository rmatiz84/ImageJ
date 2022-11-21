package ij.io;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.MemoryImageSource;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.LookUpTable;
import ij.Macro;
import ij.Prefs;
import ij.VirtualStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PointRoi;
import ij.gui.ProgressBar;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.plugin.JpegWriter;
import ij.plugin.Orthogonal_Views;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.Recorder;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FHT;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import ij.util.Tools;

public class FileMaster {
	
	public static final int DEFAULT_JPEG_QUALITY = 85;
	private static int jpegQuality;
	private static int bsize = 32768; // 32K default buffer size
	
    static {setJpegQuality(ij.Prefs.getInt(ij.Prefs.JPEG, DEFAULT_JPEG_QUALITY));}

	private static String defaultDirectory = null;
	private ImagePlus imp;
	private FileInfo fi;
	private String name;
	private String directory;
	private boolean saveName;

	/** Constructs a FileSaver from an ImagePlus. */
	public FileMaster(ImagePlus imp) {
		this.imp = imp;
		fi = imp.getFileInfo();
	}

	/** Resaves the image. Calls saveAsTiff() if this is a new image, not a TIFF,
		or if the image was loaded using a URL. Returns false if saveAsTiff() is
		called and the user selects cancel in the file save dialog box. */
	public boolean save() {
		FileInfo ofi = null;
		if (imp!=null) ofi = imp.getOriginalFileInfo();
		boolean validName = ofi!=null && imp.getTitle().equals(ofi.fileName);
		if (validName && ofi.fileFormat==Constants.TIFF && ofi.directory!=null && !ofi.directory.equals("") && (ofi.url==null||ofi.url.equals(""))) {
            name = imp.getTitle();
            directory = ofi.directory;
			String path = directory+name;
			File f = new File(path);
			if (f==null || !f.exists())
				return saveAsTiff();
			if (!IJ.isMacro()) {
				GenericDialog gd = new GenericDialog("Save as TIFF");
				gd.addMessage("\""+ofi.fileName+"\" already exists.\nDo you want to replace it?");
				gd.setOKLabel("Replace");
				gd.showDialog();
				if (gd.wasCanceled())
					return false;
			}
			IJ.showStatus("Saving "+path);
			if (imp.getStackSize()>1) {
				IJ.saveAs(imp, "tif", path);
				return true;
			} else
		    	return saveAsTiff(path);
		} else
			return saveAsTiff();
	}
	
	String getPath(String type, String extension) {
		name = imp.getTitle();
		SaveDialog sd = new SaveDialog("Save as "+type, name, extension);
		name = sd.getFileName();
		if (name==null)
			return null;
		directory = sd.getDirectory();
		imp.startTiming();
		String path = directory+name;
		return path;
	}
	
	/** Saves the image or stack in TIFF format using a save file
		dialog. Returns false if the user selects cancel. Equivalent to 
		IJ.saveAsTiff(imp,""), which is more convenient. */
	public boolean saveAsTiff() {
		String path = getPath("TIFF", ".tif");
		if (path==null)
			return false;
		if (fi.nImages>1)
			return saveAsTiffStack(path);
		else
			return saveAsTiff(path);
	}
	
	/** Saves the image in TIFF format using the specified path. Equivalent to
		 IJ.saveAsTiff(imp,path), which is more convenient. */
	public boolean saveAsTiff(String path) {
		if (fi.nImages>1)
			return saveAsTiffStack(path);
		if (imp.getProperty("FHT")!=null && path.contains("FFT of "))
			setupFFTSave();
		fi.info = imp.getInfoProperty();
		String label = imp.hasImageStack()?imp.getStack().getSliceLabel(1):null;
		if (label!=null) {
			fi.sliceLabels = new String[1];
			fi.sliceLabels[0] = label;
		}
		fi.description = getDescriptionString();
		if (imp.getProperty(Plot.PROPERTY_KEY) != null) {
			Plot plot = (Plot)(imp.getProperty(Plot.PROPERTY_KEY));
			fi.plot = plot.toByteArray();
		}
		fi.roi = RoiMaster.saveAsByteArray(imp.getRoi());
		fi.overlay = getOverlay(imp);
		fi.properties = imp.getPropertiesAsArray();
		DataOutputStream out = null;
		try {
			TiffMaster file = new TiffMaster(fi);
			out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path),bsize));
			file.write(out);
			out.close();
		} catch (IOException e) {
			showErrorMessage("saveAsTiff", path, e);
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		updateImp(fi, Constants.TIFF);
		return true;
	}
	
	private void setupFFTSave() {
		Object obj = imp.getProperty("FHT");
		if (obj==null) return;
		FHT fht = (obj instanceof FHT)?(FHT)obj:null;
		if (fht==null) return;
		if (fht.originalColorModel!=null && fht.originalBitDepth!=24)
			fht.setColorModel(fht.originalColorModel);
		ImagePlus imp2 = new ImagePlus(imp.getTitle(), fht);
		imp2.setProperty("Info", imp.getProperty("Info"));
		imp2.setProperties(imp.getPropertiesAsArray());
		imp2.setCalibration(imp.getCalibration());
		imp = imp2;
		fi = imp.getFileInfo();
	}
	
	public static byte[][] getOverlay(ImagePlus imp) {
		if (imp.getHideOverlay())
			return null;
		Overlay overlay = imp.getOverlay();
		if (overlay==null) {
			ImageCanvas ic = imp.getCanvas();
			if (ic==null) return null;
			overlay = ic.getShowAllList(); // ROI Manager "Show All" list
			if (overlay==null) return null;
		}
		int n = overlay.size();
		if (n==0)
			return null;
		if (Orthogonal_Views.isOrthoViewsImage(imp))
			return null;
		byte[][] array = new byte[n][];
		for (int i=0; i<overlay.size(); i++) {
			Roi roi = overlay.get(i);
			if (i==0)
				roi.setPrototypeOverlay(overlay);
			array[i] = RoiMaster.saveAsByteArray(roi);
		}
		return array;
	}

	/** Saves the stack as a multi-image TIFF using the specified path.
		 Equivalent to IJ.saveAsTiff(imp,path), which is more convenient. */
	public boolean saveAsTiffStack(String path) {
		if (fi.nImages==1) {
			error("This is not a stack");
			return false;
		}
		boolean virtualStack = imp.getStack().isVirtual();
		if (virtualStack)
			fi.virtualStack = (VirtualStack)imp.getStack();
		fi.info = imp.getInfoProperty();
		fi.description = getDescriptionString();
		if (virtualStack) {
			FileInfo ofi = imp.getOriginalFileInfo();
			if (path!=null && ofi!=null && path.equals(ofi.directory+ofi.fileName)) {
				error("TIFF virtual stacks cannot be saved in place.");
				return false;
			}
			String[] labels = null;
			ImageStack vs = imp.getStack();
			for (int i=1; i<=vs.getSize(); i++) {
				String label = vs.getSliceLabel(i);
				if (i==1 && label==null)
					break;
				if (labels==null)
					labels = new String[vs.getSize()];
				labels[i-1] = label;
			}
			fi.sliceLabels = labels;
		} else
			fi.sliceLabels = imp.getStack().getSliceLabels();
		fi.roi = RoiMaster.saveAsByteArray(imp.getRoi());
		fi.overlay = getOverlay(imp);
		fi.properties = imp.getPropertiesAsArray();
		if (imp.isComposite()) saveDisplayRangesAndLuts(imp, fi);
		DataOutputStream out = null;
		try {
			TiffMaster file = new TiffMaster(fi);
			out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path),bsize));
			file.write(out);
			out.close();
		} catch (IOException e) {
			showErrorMessage("saveAsTiffStack", path, e);
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		updateImp(fi, Constants.TIFF);
		return true;
	}
	
	/** Converts this image to a TIFF encoded array of bytes, 
		which can be decoded using Opener.deserialize(). */
	public byte[] serialize() {
		if (imp.getStack().isVirtual())
			return null;
		fi.info = imp.getInfoProperty();
		saveName = true;
		fi.description = getDescriptionString();
		saveName = false;
		fi.sliceLabels = imp.getStack().getSliceLabels();
		if (imp.getProperty(Plot.PROPERTY_KEY) != null) {
			Plot plot = (Plot)(imp.getProperty(Plot.PROPERTY_KEY));
			fi.plot = plot.toByteArray();
		}
		fi.roi = RoiMaster.saveAsByteArray(imp.getRoi());
		fi.overlay = getOverlay(imp);
		if (imp.isComposite()) saveDisplayRangesAndLuts(imp, fi);
		ByteArrayOutputStream out = null;
		try {
			TiffMaster encoder = new TiffMaster(fi);
			out = new ByteArrayOutputStream();
			encoder.write(out);
			out.close();
		} catch (IOException e) {
			return null;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		return out.toByteArray();
	}

	public void saveDisplayRangesAndLuts(ImagePlus imp, FileInfo fi) {
		CompositeImage ci = (CompositeImage)imp;
		int channels = imp.getNChannels();
		fi.displayRanges = new double[channels*2];
		for (int i=1; i<=channels; i++) {
			LUT lut = ci.getChannelLut(i);
			fi.displayRanges[(i-1)*2] = lut.min;
			fi.displayRanges[(i-1)*2+1] = lut.max;
		}
		if (ci.hasCustomLuts()) {
			fi.channelLuts = new byte[channels][];
			for (int i=0; i<channels; i++) {
				LUT lut = ci.getChannelLut(i+1);
				byte[] bytes = lut.getBytes();
				if (bytes==null)
					{fi.channelLuts=null; break;}
				fi.channelLuts[i] = bytes;
			}
		}	
	}

	/** Uses a save file dialog to save the image or stack as a TIFF
		in a ZIP archive. Returns false if the user selects cancel. */
	public boolean saveAsZip() {
		String path = getPath("TIFF/ZIP", ".zip");
		if (path==null)
			return false;
		else
			return saveAsZip(path);
	}
	
	/** Save the image or stack in TIFF/ZIP format using the specified path. */
	public boolean saveAsZip(String path) {
		if (imp.getProperty("FHT")!=null && path.contains("FFT of "))
			setupFFTSave();
		if (!path.endsWith(".zip"))
			path = path+".zip";
		if (name==null)
			name = imp.getTitle();
		if (name.endsWith(".zip"))
			name = name.substring(0,name.length()-4);
		if (!name.endsWith(".tif"))
			name = name+".tif";
		fi.description = getDescriptionString();
		fi.info = imp.getInfoProperty();
		fi.properties = imp.getPropertiesAsArray();
		if (imp.getProperty(Plot.PROPERTY_KEY) != null) {
			Plot plot = (Plot)(imp.getProperty(Plot.PROPERTY_KEY));
			fi.plot = plot.toByteArray();
		}
		fi.roi = RoiMaster.saveAsByteArray(imp.getRoi());
		fi.overlay = getOverlay(imp);
		fi.sliceLabels = imp.getStack().getSliceLabels();
		if (imp.isComposite()) saveDisplayRangesAndLuts(imp, fi);
		if (fi.nImages>1 && imp.getStack().isVirtual())
			fi.virtualStack = (VirtualStack)imp.getStack();
		DataOutputStream out = null;
		try {
			ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path));
			out = new DataOutputStream(new BufferedOutputStream(zos,bsize));
        	zos.putNextEntry(new ZipEntry(name));
        	TiffMaster te = new TiffMaster(fi);
			te.write(out);
			out.close();
		}
		catch (IOException e) {
			showErrorMessage("saveAsZip", path, e);
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		updateImp(fi, Constants.TIFF);
		return true;
	}

	public static boolean okForGif(ImagePlus imp) {
		if (imp.getType()==ImagePlus.COLOR_RGB)
			return false;
		else
			return true;
	}

	/** Save the image in GIF format using a save file
		dialog. Returns false if the user selects cancel
		or the image is not 8-bits. */
	public boolean saveAsGif() {
		String path = getPath("GIF", ".gif");
		if (path==null)
			return false;
		else
			return saveAsGif(path);
	}
	
	/** Save the image in Gif format using the specified path. Returns
		false if the image is not 8-bits or there is an I/O error. */
	public boolean saveAsGif(String path) {
		IJ.runPlugIn(imp, "ij.plugin.GifWriter", path);
		updateImp(fi, Constants.GIF_OR_JPG);
		return true;
	}

	/** Always returns true. */
	public static boolean okForJpeg(ImagePlus imp) {
		return true;
	}

	/** Save the image in JPEG format using a save file
		dialog. Returns false if the user selects cancel.
		@see #setJpegQuality
		@see #getJpegQuality
	*/
	public boolean saveAsJpeg() {
		String type = "JPEG ("+getJpegQuality()+")";
		String path = getPath(type, ".jpg");
		if (path==null)
			return false;
		else
			return saveAsJpeg(path);
	}

	/** Save the image in JPEG format using the specified path.
		@see #setJpegQuality
		@see #getJpegQuality
	*/
	public boolean saveAsJpeg(String path) {
		String err = JpegWriter.save(imp, path, jpegQuality);
		if (err==null && !(imp.getType()==ImagePlus.GRAY16 || imp.getType()==ImagePlus.GRAY32))
			updateImp(fi, Constants.GIF_OR_JPG);
		return true;
	}

	/** Save the image in BMP format using a save file dialog. 
		Returns false if the user selects cancel. */
	public boolean saveAsBmp() {
		String path = getPath("BMP", ".bmp");
		if (path==null)
			return false;
		else
			return saveAsBmp(path);
	}

	/** Save the image in BMP format using the specified path. */
	public boolean saveAsBmp(String path) {
		IJ.runPlugIn(imp, "ij.plugin.BMP_Writer", path);
		updateImp(fi, Constants.BMP);
		return true;
	}

	/** Saves grayscale images in PGM (portable graymap) format 
		and RGB images in PPM (portable pixmap) format,
		using a save file dialog.
		Returns false if the user selects cancel.
	*/
	public boolean saveAsPgm() {
		String extension = imp.getBitDepth()==24?".pnm":".pgm";
		String path = getPath("PGM", extension);
		if (path==null)
			return false;
		else
			return saveAsPgm(path);
	}

	/** Saves grayscale images in PGM (portable graymap) format 
		and RGB images in PPM (portable pixmap) format,
		using the specified path. */
	public boolean saveAsPgm(String path) {
		IJ.runPlugIn(imp, "ij.plugin.PNM_Writer", path);
		updateImp(fi, Constants.PGM);
		return true;
	}

	/** Save the image in PNG format using a save file dialog. 
		Returns false if the user selects cancel. */
	public boolean saveAsPng() {
		String path = getPath("PNG", ".png");
		if (path==null)
			return false;
		else
			return saveAsPng(path);
	}

	/** Save the image in PNG format using the specified path. */
	public boolean saveAsPng(String path) {
		IJ.runPlugIn(imp, "ij.plugin.PNG_Writer", path);
		updateImp(fi, Constants.IMAGEIO);
		return true;
	}

	/** Save the image in FITS format using a save file dialog. 
		Returns false if the user selects cancel. */
	public boolean saveAsFits() {
		if (!okForFits(imp)) return false;
		String path = getPath("FITS", ".fits");
		if (path==null)
			return false;
		else
			return saveAsFits(path);
	}

	/** Save the image in FITS format using the specified path. */
	public boolean saveAsFits(String path) {
		if (!okForFits(imp)) return false;
		IJ.runPlugIn(imp, "ij.plugin.FITS_Writer", path);
		updateImp(fi, Constants.FITS);
		return true;
	}

	public static boolean okForFits(ImagePlus imp) {
		if (imp.getBitDepth()==24) {
			IJ.error("FITS Writer", "Grayscale image required");
			return false;
		} else
			return true;
	}

	/** Save the image or stack as raw data using a save file
		dialog. Returns false if the user selects cancel. */
	public boolean saveAsRaw() {
		String path = getPath("Raw", ".raw");
		if (path==null)
			return false;
		if (imp.getStackSize()==1)
			return saveAsRaw(path);
		else
			return saveAsRawStack(path);
	}
	
	/** Save the image as raw data using the specified path. */
	public boolean saveAsRaw(String path) {
		fi.nImages = 1;
		fi.intelByteOrder = Prefs.intelByteOrder;
		boolean signed16Bit = false;
		short[] pixels = null;
		int n = 0;
		OutputStream out = null;
		try {
			signed16Bit = imp.getCalibration().isSigned16Bit();
			if (signed16Bit) {
				pixels = (short[])imp.getProcessor().getPixels();
				n = imp.getWidth()*imp.getHeight();
				for (int i=0; i<n; i++)
					pixels[i] = (short)(pixels[i]-32768);
			}
			ImageWriter file = new ImageWriter(fi);
			out = new BufferedOutputStream(new FileOutputStream(path),bsize);
			file.write(out);
			out.close();
		}
		catch (IOException e) {
			showErrorMessage("saveAsRaw", path, e);
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		if (signed16Bit) {
			for (int i=0; i<n; i++)
			pixels[i] = (short)(pixels[i]+32768);
		}
		updateImp(fi, Constants.RAW);
		return true;
	}

	/** Save the stack as raw data using the specified path. */
	public boolean saveAsRawStack(String path) {
		if (fi.nImages==1)
			{IJ.log("This is not a stack"); return false;}
		fi.intelByteOrder = Prefs.intelByteOrder;
		boolean signed16Bit = false;
		Object[] stack = null;
		int n = 0;
		boolean virtualStack = imp.getStackSize()>1 && imp.getStack().isVirtual();
		if (virtualStack) {
			fi.virtualStack = (VirtualStack)imp.getStack();
			if (imp.getProperty("AnalyzeFormat")!=null) fi.fileName="FlipTheseImages";
		}
		OutputStream out = null;
		try {
			signed16Bit = imp.getCalibration().isSigned16Bit();
			if (signed16Bit && !virtualStack) {
				stack = (Object[])fi.pixels;
				n = imp.getWidth()*imp.getHeight();
				for (int slice=0; slice<fi.nImages; slice++) {
					short[] pixels = (short[])stack[slice];
					for (int i=0; i<n; i++)
						pixels[i] = (short)(pixels[i]-32768);
				}
			}
			ImageWriter file = new ImageWriter(fi);
			out = new BufferedOutputStream(new FileOutputStream(path),bsize);
			file.write(out);
			out.close();
		} catch (IOException e) {
			showErrorMessage("saveAsRawStack", path, e);
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		if (signed16Bit) {
			for (int slice=0; slice<fi.nImages; slice++) {
				short[] pixels = (short[])stack[slice];
				for (int i=0; i<n; i++)
					pixels[i] = (short)(pixels[i]+32768);
			}
		}
		updateImp(fi, Constants.RAW);
		return true;
	}

	/** Save the image as tab-delimited text using a save file
		dialog. Returns false if the user selects cancel. */
	public boolean saveAsText() {
		String path = getPath("Text", ".txt");
		if (path==null)
			return false;
		return saveAsText(path);
	}
	
	/** Save the image as tab-delimited text using the specified path. */
	public boolean saveAsText(String path) {
		DataOutputStream out = null;
		try {
			Calibration cal = imp.getCalibration();
			int precision = Analyzer.getPrecision();
			int measurements = Analyzer.getMeasurements();
			boolean scientificNotation = (measurements&Measurements.SCIENTIFIC_NOTATION)!=0;
			if (scientificNotation)
				precision = -precision;
			TextEncoder file = new TextEncoder(imp.getProcessor(), cal, precision);
			out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
			file.write(out);
			out.close();
		} catch (IOException e) {
			showErrorMessage("saveAsText", path, e);
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		return true;
	}

	/** Save the current LUT using a save file
		dialog. Returns false if the user selects cancel. */
	public boolean saveAsLut() {
		if (imp.getType()==ImagePlus.COLOR_RGB) {
			error("RGB Images do not have a LUT.");
			return false;
		}
		String path = getPath("LUT", ".lut");
		if (path==null)
			return false;
		return saveAsLut(path);
	}
	
	/** Save the current LUT using the specified path. */
	public boolean saveAsLut(String path) {
		LookUpTable lut = imp.createLut();
		int mapSize = lut.getMapSize();
		if (mapSize==0) {
			error("RGB Images do not have a LUT.");
			return false;
		}
		if (mapSize<256) {
			error("Cannot save LUTs with less than 256 entries.");
			return false;
		}
		byte[] reds = lut.getReds(); 
		byte[] greens = lut.getGreens();
		byte[] blues = lut.getBlues();
		byte[] pixels = new byte[768];
		for (int i=0; i<256; i++) {
			pixels[i] = reds[i];
			pixels[i+256] = greens[i];
			pixels[i+512] = blues[i];
		}
		FileInfo fi = new FileInfo();
		fi.width = 768;
		fi.height = 1;
		fi.pixels = pixels;

		OutputStream out = null;
		try {
			ImageWriter file = new ImageWriter(fi);
			out = new FileOutputStream(path);
			file.write(out);
			out.close();
		}
		catch (IOException e) {
			showErrorMessage("saveAsLut", path, e);
			return false;
		} finally {
			if (out!=null)
				try {out.close();} catch (IOException e) {}
		}
		return true;
	}

	public void updateImagePlus(String path, int fileFormat) {
		if (imp==null || fi==null)
			return;
		if (name==null && path!=null) {
			File f = new File(path);
			directory = f.getParent() + File.separator;
			name = f.getName();
		}
		updateImp(fi, fileFormat);
	}
	
	private void updateImp(FileInfo fi, int fileFormat) {
		imp.changes = false;
		if (name!=null) {
			fi.fileFormat = fileFormat;
			FileInfo ofi = imp.getOriginalFileInfo();
			if (ofi!=null) {
				if (ofi.openNextName==null) {
					fi.openNextName = ofi.fileName;
					fi.openNextDir = ofi.directory;
				} else {
					fi.openNextName = ofi.openNextName;
					fi.openNextDir = ofi.openNextDir ;
				}
			}
			fi.fileName = name;
			fi.directory = directory;
			//if (fileFormat==fi.TIFF)
			//	fi.offset = TiffEncoder.IMAGE_START;
			fi.description = null;
			imp.setTitle(name);
			fi.imageSaved = true;
			imp.setFileInfo(fi);
		}
	}

	private void showErrorMessage(String title, String path, IOException e) {
		String msg = e.getMessage();
		if (msg.length()>100)
			msg = msg.substring(0, 100);
		msg = "File saving error (IOException):\n   \"" + msg + "\"";
		IJ.error("FileSaver."+title, msg+" \n   "+path);
		IJ.showProgress(1.0);
	}
	
	private void error(String msg) {
		IJ.error("FileSaver", msg);
	}

	/** Returns a string containing information about the specified  image. */
	public String getDescriptionString() {
		Calibration cal = imp.getCalibration();
		StringBuffer sb = new StringBuffer(100);
		sb.append("ImageJ="+ImageJ.VERSION+"\n");
		if (fi.nImages>1 && fi.fileType!=Constants.RGB48)
			sb.append("images="+fi.nImages+"\n");
		int channels = imp.getNChannels();
		if (channels>1)
			sb.append("channels="+channels+"\n");
		int slices = imp.getNSlices();
		if (slices>1)
			sb.append("slices="+slices+"\n");
		int frames = imp.getNFrames();
		if (frames>1)
			sb.append("frames="+frames+"\n");
		if (imp.isHyperStack()) sb.append("hyperstack=true\n");
		if (imp.isComposite()) {
			String mode = ((CompositeImage)imp).getModeAsString();
			sb.append("mode="+mode+"\n");
		}
		if (fi.unit!=null)
			appendEscapedLine(sb, "unit="+fi.unit);
		int bitDepth = imp.getBitDepth();
		if (fi.valueUnit!=null && (fi.calibrationFunction!=Calibration.CUSTOM||bitDepth==32)) {
			if (bitDepth!=32) {
				sb.append("cf="+fi.calibrationFunction+"\n");
				if (fi.coefficients!=null) {
					for (int i=0; i<fi.coefficients.length; i++)
						sb.append("c"+i+"="+fi.coefficients[i]+"\n");
				}
			}
			appendEscapedLine(sb, "vunit="+fi.valueUnit);
			if (cal.zeroClip() && bitDepth!=32)
				sb.append("zeroclip=true\n");
		}
		// get stack z-spacing, more units and fps
		if (cal.frameInterval!=0.0) {
			if ((int)cal.frameInterval==cal.frameInterval)
				sb.append("finterval="+(int)cal.frameInterval+"\n");
			else
				sb.append("finterval="+cal.frameInterval+"\n");
		}
		if (!cal.getTimeUnit().equals("sec"))
			appendEscapedLine(sb, "tunit="+cal.getTimeUnit());
		if (!cal.getYUnit().equals(cal.getUnit()))
			appendEscapedLine(sb, "yunit="+cal.getYUnit());
		if (!cal.getZUnit().equals(cal.getUnit()))
			appendEscapedLine(sb, "zunit="+cal.getZUnit());
		if (fi.nImages>1) {
			if (fi.pixelDepth!=1.0)
				sb.append("spacing="+fi.pixelDepth+"\n");
			if (cal.fps!=0.0) {
				if ((int)cal.fps==cal.fps)
					sb.append("fps="+(int)cal.fps+"\n");
				else
					sb.append("fps="+cal.fps+"\n");
			}
			sb.append("loop="+(cal.loop?"true":"false")+"\n");
		}

		// get min and max display values
		ImageProcessor ip = imp.getProcessor();
		double min = ip.getMin();
		double max = ip.getMax();
		int type = imp.getType();
		boolean enhancedLut = (type==ImagePlus.GRAY8 || type==ImagePlus.COLOR_256) && (min!=0.0 || max !=255.0);
		if (enhancedLut || type==ImagePlus.GRAY16 || type==ImagePlus.GRAY32) {
			sb.append("min="+min+"\n");
			sb.append("max="+max+"\n");
		}
		
		// get non-zero origins
		if (cal.xOrigin!=0.0)
			sb.append("xorigin="+cal.xOrigin+"\n");
		if (cal.yOrigin!=0.0)
			sb.append("yorigin="+cal.yOrigin+"\n");
		if (cal.zOrigin!=0.0)
			sb.append("zorigin="+cal.zOrigin+"\n");
		if (cal.info!=null && cal.info.length()<=64 && cal.info.indexOf('=')==-1 && cal.info.indexOf('\n')==-1)
			appendEscapedLine(sb, "info="+cal.info);
			
		// get invertY flag
		if (cal.getInvertY())
			sb.append("inverty=true\n");

		if (saveName)
			appendEscapedLine(sb, "name="+imp.getTitle());
			
		if (imp.getType()==ImagePlus.COLOR_256)
			sb.append("8bitcolor=true\n");

		sb.append((char)0);
		return new String(sb);
	}

	// Append a string to a StringBuffer with escaped special characters as needed for java.util.Properties,
	// and add a linefeed character
	void appendEscapedLine(StringBuffer sb, String str) {
		for (int i=0; i<str.length(); i++) {
			char c = str.charAt(i);
			if (c>=0x20 && c<0x7f && c!='\\')
				sb.append(c);
			else if (c<=0xffff) {   //(supplementary unicode characters >0xffff unsupported)
				sb.append("\\u");
				sb.append(Tools.int2hex(c, 4));
			}
		}
		sb.append('\n');
	}

	/** Specifies the image quality (0-100). 0 is poorest image quality,
		highest compression, and 100 is best image quality, lowest compression. */
    public static void setJpegQuality(int quality) {
        jpegQuality = quality;
    	if (jpegQuality<0) jpegQuality = 0;
    	if (jpegQuality>100) jpegQuality = 100;
    }

    /** Returns the current JPEG quality setting (0-100). */
    public static int getJpegQuality() {
        return jpegQuality;
    }
    
    /** Sets the BufferedOutputStream buffer size in bytes (default is 32K). */
    public static void setBufferSize(int bufferSize) {
        bsize = bufferSize;
        if (bsize<2048) bsize = 2048;
    }
    
    
    
    
    
    
	private int width, height;
	private static boolean showConflictMessage = true;
	private double minValue, maxValue;
	private static boolean silentMode;

	public FileMaster(FileInfo fi) {
		this.fi = fi;
		if (fi!=null) {
			width = fi.width;
			height = fi.height;
		}
		if (IJ.debugMode) IJ.log("FileInfo: "+fi);
	}
	
	/** Opens the image and returns it has an ImagePlus object. */
	public ImagePlus openImage() {
		boolean wasRecording = Recorder.record;
		Recorder.record = false;
		ImagePlus imp = open(false);
		Recorder.record = wasRecording;
		return imp;
	}

	/** Opens the image and displays it. */
	public void open() {
		open(true);
	}
	
	/** Obsolete, replaced by openImage() and open(). */
	public ImagePlus open(boolean show) {

		ImagePlus imp=null;
		Object pixels;
		ProgressBar pb=null;
	    ImageProcessor ip;
		
		ColorModel cm = createColorModel(fi);
		if (fi.nImages>1)
			return openStack(cm, show);
		switch (fi.fileType) {
			case Constants.GRAY8:
			case Constants.COLOR8:
			case Constants.BITMAP:
				pixels = readPixels(fi);
				if (pixels==null) return null;
				ip = new ByteProcessor(width, height, (byte[])pixels, cm);
    			imp = new ImagePlus(fi.fileName, ip);
				break;
			case Constants.GRAY16_SIGNED:
			case Constants.GRAY16_UNSIGNED:
			case Constants.GRAY12_UNSIGNED:
				pixels = readPixels(fi);
				if (pixels==null) return null;
	    		ip = new ShortProcessor(width, height, (short[])pixels, cm);
       			imp = new ImagePlus(fi.fileName, ip);
				break;
			case Constants.GRAY32_INT:
			case Constants.GRAY32_UNSIGNED:
			case Constants.GRAY32_FLOAT:
			case Constants.GRAY24_UNSIGNED:
			case Constants.GRAY64_FLOAT:
				pixels = readPixels(fi);
				if (pixels==null) return null;
	    		ip = new FloatProcessor(width, height, (float[])pixels, cm);
       			imp = new ImagePlus(fi.fileName, ip);
				break;
			case Constants.RGB:
			case Constants.BGR:
			case Constants.ARGB:
			case Constants.ABGR:
			case Constants.BARG:
			case Constants.RGB_PLANAR:
			case Constants.CMYK:
				pixels = readPixels(fi);
				if (pixels==null) return null;
				ip = new ColorProcessor(width, height, (int[])pixels);
				if (fi.fileType==Constants.CMYK)
					ip.invert();
				imp = new ImagePlus(fi.fileName, ip);
				break;
			case Constants.RGB48:
			case Constants.RGB48_PLANAR:
				boolean planar = fi.fileType==Constants.RGB48_PLANAR;
				Object[] pixelArray = (Object[])readPixels(fi);
				if (pixelArray==null) return null;
				int nChannels = 3;
				ImageStack stack = new ImageStack(width, height);
				stack.addSlice("Red", pixelArray[0]);
				stack.addSlice("Green", pixelArray[1]);
				stack.addSlice("Blue", pixelArray[2]);
				if (fi.samplesPerPixel==4 && pixelArray.length==4) {
					stack.addSlice("Gray", pixelArray[3]);
					nChannels = 4;
				}
        		imp = new ImagePlus(fi.fileName, stack);
        		imp.setDimensions(nChannels, 1, 1);
        		if (planar)
        			imp.getProcessor().resetMinAndMax();
				imp.setFileInfo(fi);
				int mode = IJ.COMPOSITE;
				if (fi.description!=null) {
					if (fi.description.indexOf("mode=color")!=-1)
					mode = IJ.COLOR;
					else if (fi.description.indexOf("mode=gray")!=-1)
					mode = IJ.GRAYSCALE;
				}
        		imp = new CompositeImage(imp, mode);
        		if (!planar && fi.displayRanges==null) {
        			if (nChannels==4)
        				((CompositeImage)imp).resetDisplayRanges();
        			else {
						for (int c=1; c<=3; c++) {
							imp.setPosition(c, 1, 1);
							imp.setDisplayRange(minValue, maxValue);
						}
						imp.setPosition(1, 1, 1);
       				}
        		}
        		if (fi.whiteIsZero) // cmyk?
        			IJ.run(imp, "Invert", "");
				break;
		}
		imp.setFileInfo(fi);
		setCalibration(imp);
		if (fi.info!=null)
			imp.setProperty("Info", fi.info);
		if (fi.sliceLabels!=null&&fi.sliceLabels.length==1&&fi.sliceLabels[0]!=null)
			imp.setProp("Slice_Label", fi.sliceLabels[0]);
		if (fi.plot!=null) try {
			Plot plot = new Plot(imp, new ByteArrayInputStream(fi.plot));
			imp.setProperty(Plot.PROPERTY_KEY, plot);
		} catch (Exception e) { IJ.handleException(e); }
		if (fi.roi!=null)
			decodeAndSetRoi(imp, fi);
		if (fi.overlay!=null)
			setOverlay(imp, fi.overlay);
		if (fi.properties!=null)
			imp.setProperties(fi.properties);
		if (show) imp.show();
		return imp;
	}
	
	public ImageProcessor openProcessor() {
		Object pixels;
		ProgressBar pb=null;
		ImageProcessor ip = null;		
		ColorModel cm = createColorModel(fi);
		switch (fi.fileType) {
			case Constants.GRAY8:
			case Constants.COLOR8:
			case Constants.BITMAP:
				pixels = readPixels(fi);
				if (pixels==null) return null;
				ip = new ByteProcessor(width, height, (byte[])pixels, cm);
				break;
			case Constants.GRAY16_SIGNED:
			case Constants.GRAY16_UNSIGNED:
			case Constants.GRAY12_UNSIGNED:
				pixels = readPixels(fi);
				if (pixels==null) return null;
	    		ip = new ShortProcessor(width, height, (short[])pixels, cm);
				break;
			case Constants.GRAY32_INT:
			case Constants.GRAY32_UNSIGNED:
			case Constants.GRAY32_FLOAT:
			case Constants.GRAY24_UNSIGNED:
			case Constants.GRAY64_FLOAT:
				pixels = readPixels(fi);
				if (pixels==null) return null;
	    		ip = new FloatProcessor(width, height, (float[])pixels, cm);
				break;
			case Constants.RGB:
			case Constants.BGR:
			case Constants.ARGB:
			case Constants.ABGR:
			case Constants.BARG:
			case Constants.RGB_PLANAR:
			case Constants.CMYK:
				pixels = readPixels(fi);
				if (pixels==null) return null;
				ip = new ColorProcessor(width, height, (int[])pixels);
				if (fi.fileType==Constants.CMYK)
					ip.invert();
				break;
		}
		return ip;
	}

	void setOverlay(ImagePlus imp, byte[][] rois) {
		Overlay overlay = new Overlay();
		Overlay proto = null;
		for (int i=0; i<rois.length; i++) {
			Roi roi = RoiMaster.openFromByteArray(rois[i]);
			if (roi==null)
				continue;
			if (proto==null) {
				proto = roi.getPrototypeOverlay();
				overlay.drawLabels(proto.getDrawLabels());
				overlay.drawNames(proto.getDrawNames());
				overlay.drawBackgrounds(proto.getDrawBackgrounds());
				overlay.setLabelColor(proto.getLabelColor());
				overlay.setLabelFont(proto.getLabelFont(), proto.scalableLabels());
			}
			overlay.add(roi);
		}
		imp.setOverlay(overlay);
	}

	/** Opens a stack of images. */
	ImagePlus openStack(ColorModel cm, boolean show) {
		ImageStack stack = new ImageStack(fi.width, fi.height, cm);
		long skip = fi.getOffset();
		Object pixels;
		try {
			ImageReader reader = new ImageReader(fi);
			InputStream is = createInputStream(fi);
			if (is==null)
				return null;
			IJ.resetEscape();
			for (int i=1; i<=fi.nImages; i++) {
				if (!silentMode)
					IJ.showStatus("Reading: " + i + "/" + fi.nImages);
				if (IJ.escapePressed()) {
					IJ.beep();
					IJ.showProgress(1.0);
					silentMode = false;
					return null;
				}
				pixels = reader.readPixels(is, skip);
				if (pixels==null)
					break;
				stack.addSlice(null, pixels);
				skip = fi.getGap();
				if (!silentMode)
					IJ.showProgress(i, fi.nImages);
			}
			is.close();
		}
		catch (Exception e) {
			IJ.log("" + e);
		}
		catch(OutOfMemoryError e) {
			IJ.outOfMemory(fi.fileName);
			stack.trim();
		}
		if (!silentMode) IJ.showProgress(1.0);
		if (stack.size()==0)
			return null;
		if (fi.sliceLabels!=null && fi.sliceLabels.length<=stack.size()) {
			for (int i=0; i<fi.sliceLabels.length; i++)
				stack.setSliceLabel(fi.sliceLabels[i], i+1);
		}
		ImagePlus imp = new ImagePlus(fi.fileName, stack);
		if (fi.info!=null)
			imp.setProperty("Info", fi.info);
		if (fi.roi!=null)
			decodeAndSetRoi(imp, fi);
		if (fi.overlay!=null)
			setOverlay(imp, fi.overlay);
		if (fi.properties!=null)
			imp.setProperties(fi.properties);
		if (show) imp.show();
		imp.setFileInfo(fi);
		setCalibration(imp);
		ImageProcessor ip = imp.getProcessor();
		if (ip.getMin()==ip.getMax())  // find stack min and max if first slice is blank
			setStackDisplayRange(imp);
		if (!silentMode) IJ.showProgress(1.0);
		return imp;
	}
	
	private void decodeAndSetRoi(ImagePlus imp, FileInfo fi) {
		Roi roi = RoiMaster.openFromByteArray(fi.roi);
		imp.setRoi(roi);
		if ((roi instanceof PointRoi) && ((PointRoi)roi).getNCounters()>1) 
			IJ.setTool("multi-point");
	}

	void setStackDisplayRange(ImagePlus imp) {
		ImageStack stack = imp.getStack();
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		int n = stack.size();
		for (int i=1; i<=n; i++) {
			if (!silentMode)
				IJ.showStatus("Calculating stack min and max: "+i+"/"+n);
			ImageProcessor ip = stack.getProcessor(i);
			ip.resetMinAndMax();
			if (ip.getMin()<min)
				min = ip.getMin();
			if (ip.getMax()>max)
				max = ip.getMax();
		}
		imp.getProcessor().setMinAndMax(min, max);
		imp.updateAndDraw();
	}
	
	/** Restores the original version of the specified image. */
	public void revertToSaved(ImagePlus imp) {
		if (fi==null)
			return;
		String path = fi.getFilePath();
		if (fi.url!=null && !fi.url.equals("") && (fi.directory==null||fi.directory.equals("")))
			path = fi.url;
		IJ.showStatus("Loading: " + path);
		ImagePlus imp2 = null;
		if (!path.endsWith(".raw"))
			imp2 = IJ.openImage(path);
		if (imp2!=null)
			imp.setImage(imp2);
		else {
			if (fi.nImages>1)
				return;
			Object pixels = readPixels(fi);
			if (pixels==null) return;
			ColorModel cm = createColorModel(fi);
			ImageProcessor ip = null;
			switch (fi.fileType) {
				case Constants.GRAY8:
				case Constants.COLOR8:
				case Constants.BITMAP:
					ip = new ByteProcessor(width, height, (byte[])pixels, cm);
					imp.setProcessor(null, ip);
					break;
				case Constants.GRAY16_SIGNED:
				case Constants.GRAY16_UNSIGNED:
				case Constants.GRAY12_UNSIGNED:
					ip = new ShortProcessor(width, height, (short[])pixels, cm);
					imp.setProcessor(null, ip);
					break;
				case Constants.GRAY32_INT:
				case Constants.GRAY32_FLOAT:
					ip = new FloatProcessor(width, height, (float[])pixels, cm);
					imp.setProcessor(null, ip);
					break;
				case Constants.RGB:
				case Constants.BGR:
				case Constants.ARGB:
				case Constants.ABGR:
				case Constants.RGB_PLANAR:
					Image img = Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(width, height, (int[])pixels, 0, width));
					imp.setImage(img);
					break;
				case Constants.CMYK:
					ip = new ColorProcessor(width, height, (int[])pixels);
					ip.invert();
					imp.setProcessor(null, ip);
					break;
			}
		}
	}
	
	void setCalibration(ImagePlus imp) {
		if (fi.fileType==Constants.GRAY16_SIGNED) {
			if (IJ.debugMode) IJ.log("16-bit signed");
 			imp.getLocalCalibration().setSigned16BitCalibration();
		}
		Properties props = decodeDescriptionString(fi);
		Calibration cal = imp.getCalibration();
		boolean calibrated = false;
		if (fi.pixelWidth>0.0 && fi.unit!=null) {
			double threshold = 0.001;
			if (fi.description!=null && fi.description.startsWith("ImageJ"))
				threshold = 0.0001;
			if (Prefs.convertToMicrons && fi.pixelWidth<=threshold && fi.unit.equals("cm")) {
				fi.pixelWidth *= 10000.0;
				fi.pixelHeight *= 10000.0;
				if (fi.pixelDepth!=1.0)
					fi.pixelDepth *= 10000.0;
				fi.unit = "um";
			}
			cal.pixelWidth = fi.pixelWidth;
			cal.pixelHeight = fi.pixelHeight;
			cal.pixelDepth = fi.pixelDepth;
			cal.setUnit(fi.unit);
			calibrated = true;
		}
		
		if (fi.valueUnit!=null) {
			if (imp.getBitDepth()==32)
				cal.setValueUnit(fi.valueUnit);
			else {
				int f = fi.calibrationFunction;
				if ((f>=Calibration.STRAIGHT_LINE && f<=Calibration.EXP_RECOVERY && fi.coefficients!=null)
				|| f==Calibration.UNCALIBRATED_OD) {
					boolean zeroClip = props!=null && props.getProperty("zeroclip", "false").equals("true");	
					cal.setFunction(f, fi.coefficients, fi.valueUnit, zeroClip);
					calibrated = true;
				}
			}
		}
		
		if (calibrated)
			checkForCalibrationConflict(imp, cal);
		
		if (fi.frameInterval!=0.0)
			cal.frameInterval = fi.frameInterval;
		
		if (props==null)
			return;
					
		cal.xOrigin = getDouble(props,"xorigin");
		cal.yOrigin = getDouble(props,"yorigin");
		cal.zOrigin = getDouble(props,"zorigin");
		cal.setInvertY(getBoolean(props, "inverty"));
		cal.info = props.getProperty("info");		
				
		cal.fps = getDouble(props,"fps");
		cal.loop = getBoolean(props, "loop");
		cal.frameInterval = getDouble(props,"finterval");
		cal.setTimeUnit(props.getProperty("tunit", "sec"));
		cal.setYUnit(props.getProperty("yunit"));
		cal.setZUnit(props.getProperty("zunit"));

		double displayMin = getDouble(props,"min");
		double displayMax = getDouble(props,"max");
		if (!(displayMin==0.0&&displayMax==0.0)) {
			int type = imp.getType();
			ImageProcessor ip = imp.getProcessor();
			if (type==ImagePlus.GRAY8 || type==ImagePlus.COLOR_256)
				ip.setMinAndMax(displayMin, displayMax);
			else if (type==ImagePlus.GRAY16 || type==ImagePlus.GRAY32) {
				if (ip.getMin()!=displayMin || ip.getMax()!=displayMax)
					ip.setMinAndMax(displayMin, displayMax);
			}
		}
		
		if (getBoolean(props, "8bitcolor"))
			imp.setTypeToColor256(); // set type to COLOR_256
		
		int stackSize = imp.getStackSize();
		if (stackSize>1) {
			int channels = (int)getDouble(props,"channels");
			int slices = (int)getDouble(props,"slices");
			int frames = (int)getDouble(props,"frames");
			if (channels==0) channels = 1;
			if (slices==0) slices = 1;
			if (frames==0) frames = 1;
			//IJ.log("setCalibration: "+channels+"  "+slices+"  "+frames);
			if (channels*slices*frames==stackSize) {
				imp.setDimensions(channels, slices, frames);
				if (getBoolean(props, "hyperstack"))
					imp.setOpenAsHyperStack(true);
			}
		}
	}

		
	void checkForCalibrationConflict(ImagePlus imp, Calibration cal) {
		Calibration gcal = imp.getGlobalCalibration();
		if  (gcal==null || !showConflictMessage || IJ.isMacro())
			return;
		if (cal.pixelWidth==gcal.pixelWidth && cal.getUnit().equals(gcal.getUnit()))
			return;
		GenericDialog gd = new GenericDialog(imp.getTitle());
		gd.addMessage("The calibration of this image conflicts\nwith the current global calibration.");
		gd.addCheckbox("Disable_Global Calibration", true);
		gd.addCheckbox("Disable_these Messages", false);
		gd.showDialog();
		if (gd.wasCanceled()) return;
		boolean disable = gd.getNextBoolean();
		if (disable) {
			imp.setGlobalCalibration(null);
			imp.setCalibration(cal);
			WindowManager.repaintImageWindows();
		}
		boolean dontShow = gd.getNextBoolean();
		if (dontShow) showConflictMessage = false;
	}

	/** Returns an IndexColorModel for the image specified by this FileInfo. */
	public ColorModel createColorModel(FileInfo fi) {
		if (fi.lutSize>0)
			return new IndexColorModel(8, fi.lutSize, fi.reds, fi.greens, fi.blues);
		else
			return LookUpTable.createGrayscaleColorModel(fi.whiteIsZero);
	}

	/** Returns an InputStream for the image described by this FileInfo. */
	public InputStream createInputStream(FileInfo fi) throws IOException, MalformedURLException {
		InputStream is = null;
		boolean gzip = fi.fileName!=null && (fi.fileName.endsWith(".gz")||fi.fileName.endsWith(".GZ"));
		if (fi.inputStream!=null)
			is = fi.inputStream;
		else if (fi.url!=null && !fi.url.equals(""))
			is = new URL(fi.url+fi.fileName).openStream();
		else {
			if (fi.directory!=null && fi.directory.length()>0 && !(fi.directory.endsWith(Prefs.separator)||fi.directory.endsWith("/")))
				fi.directory += Prefs.separator;
		    File f = new File(fi.getFilePath());
		    if (gzip) fi.compression = Constants.COMPRESSION_UNKNOWN;
		    if (f==null || !f.exists() || f.isDirectory() || !validateFileInfo(f, fi))
		    	is = null;
		    else
				is = new FileInputStream(f);
		}
		if (is!=null) {
			if (fi.compression>=Constants.LZW)
				is = new RandomAccessStream(is);
			else if (gzip)
				is = new GZIPInputStream(is, 50000);
		}
		return is;
	}
	
	static boolean validateFileInfo(File f, FileInfo fi) {
		long offset = fi.getOffset();
		long length = 0;
		if (fi.width<=0 || fi.height<=0) {
		   error("Width or height <= 0.", fi, offset, length);
		   return false;
		}
		if (offset>=0 && offset<1000L)
			 return true;
		if (offset<0L) {
		   error("Offset is negative.", fi, offset, length);
		   return false;
		}
		if (fi.fileType==Constants.BITMAP || fi.compression!=Constants.COMPRESSION_NONE)
			return true;
		length = f.length();
		long size = fi.width*fi.height*fi.getBytesPerPixel();
		size = fi.nImages>1?size:size/4;
		if (fi.height==1) size = 0; // allows plugins to read info of unknown length at end of file
		if (offset+size>length) {
		   error("Offset + image size > file length.", fi, offset, length);
		   return false;
		}
		return true;
	}

	static void error(String msg, FileInfo fi, long offset, long length) {
		String msg2 = "FileInfo parameter error. \n"
			+msg + "\n \n"
			+"  Width: " + fi.width + "\n"
			+"  Height: " + fi.height + "\n"
			+"  Offset: " + offset + "\n"
			+"  Bytes/pixel: " + fi.getBytesPerPixel() + "\n"
			+(length>0?"  File length: " + length + "\n":"");
		if (silentMode) {
			IJ.log("Error opening "+fi.getFilePath());
			IJ.log(msg2);
		} else
			IJ.error("FileOpener", msg2);
	}


	/** Reads the pixel data from an image described by a FileInfo object. */
	Object readPixels(FileInfo fi) {
		Object pixels = null;
		try {
			InputStream is = createInputStream(fi);
			if (is==null)
				return null;
			ImageReader reader = new ImageReader(fi);
			pixels = reader.readPixels(is);
			minValue = reader.min;
			maxValue = reader.max;
			is.close();
		}
		catch (Exception e) {
			if (!Macro.MACRO_CANCELED.equals(e.getMessage()))
				IJ.handleException(e);
		}
		return pixels;
	}

	public Properties decodeDescriptionString(FileInfo fi) {
		if (fi.description==null || fi.description.length()<7)
			return null;
		if (IJ.debugMode)
			IJ.log("Image Description: " + new String(fi.description).replace('\n',' '));
		if (!fi.description.startsWith("ImageJ"))
			return null;
		Properties props = new Properties();
		InputStream is = new ByteArrayInputStream(fi.description.getBytes());
		try {props.load(is); is.close();}
		catch (IOException e) {return null;}
		String dsUnit = props.getProperty("unit","");
		if ("cm".equals(fi.unit) && "um".equals(dsUnit)) {
			fi.pixelWidth *= 10000;
			fi.pixelHeight *= 10000;
		}
		fi.unit = dsUnit;
		Double n = getNumber(props,"cf");
		if (n!=null) fi.calibrationFunction = n.intValue();
		double c[] = new double[5];
		int count = 0;
		for (int i=0; i<5; i++) {
			n = getNumber(props,"c"+i);
			if (n==null) break;
			c[i] = n.doubleValue();
			count++;
		}
		if (count>=2) {
			fi.coefficients = new double[count];
			for (int i=0; i<count; i++)
				fi.coefficients[i] = c[i];			
		}
		fi.valueUnit = props.getProperty("vunit");
		n = getNumber(props,"images");
		if (n!=null && n.doubleValue()>1.0)
		fi.nImages = (int)n.doubleValue();
		n = getNumber(props, "spacing");
		if (n!=null) {
			double spacing = n.doubleValue();
			if (spacing<0) spacing = -spacing;
			fi.pixelDepth = spacing;
		}
		String name = props.getProperty("name");
		if (name!=null)
			fi.fileName = name;
		return props;
	}

	private Double getNumber(Properties props, String key) {
		String s = props.getProperty(key);
		if (s!=null) {
			try {
				return Double.valueOf(s);
			} catch (NumberFormatException e) {}
		}	
		return null;
	}
	
	private double getDouble(Properties props, String key) {
		Double n = getNumber(props, key);
		return n!=null?n.doubleValue():0.0;
	}
	
	private boolean getBoolean(Properties props, String key) {
		String s = props.getProperty(key);
		return s!=null&&s.equals("true")?true:false;
	}
	
	public static void setShowConflictMessage(boolean b) {
		showConflictMessage = b;
	}
	
	static void setSilentMode(boolean mode) {
		silentMode = mode;
	}



    

}
