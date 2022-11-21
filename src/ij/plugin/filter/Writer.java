package ij.plugin.filter;
import ij.ImagePlus;
import ij.io.FileMaster;
import ij.process.ImageProcessor;


/** Obsolete
* @deprecated
*/
public class Writer implements PlugInFilter {
	private String arg;
    private ImagePlus imp;
    
	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		this.imp = imp;
		return DOES_ALL+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		if (arg.equals("tiff"))
			new FileMaster(imp).saveAsTiff();
		else if (arg.equals("gif"))
			new FileMaster(imp).saveAsGif();
		else if (arg.equals("jpeg"))
			new FileMaster(imp).saveAsJpeg();
		else if (arg.equals("text"))
			new FileMaster(imp).saveAsText();
		else if (arg.equals("lut"))
			new FileMaster(imp).saveAsLut();
		else if (arg.equals("raw"))
			new FileMaster(imp).saveAsRaw();
		else if (arg.equals("zip"))
			new FileMaster(imp).saveAsZip();
		else if (arg.equals("bmp"))
			new FileMaster(imp).saveAsBmp();
		else if (arg.equals("png"))
			new FileMaster(imp).saveAsPng();
		else if (arg.equals("pgm"))
			new FileMaster(imp).saveAsPgm();
		else if (arg.equals("fits"))
			new FileMaster(imp).saveAsFits();
	}
	
}


