package ij.io;

public class Constants {
	
	/** 8-bit unsigned integer (0-255). */
	public static final int GRAY8 = 0;
	
	/**	16-bit signed integer (-32768-32767). Imported signed images
		are converted to unsigned by adding 32768. */
	public static final int GRAY16_SIGNED = 1;
	
	/** 16-bit unsigned integer (0-65535). */
	public static final int GRAY16_UNSIGNED = 2;
	
	/**	32-bit signed integer. Imported 32-bit integer images are
		converted to floating-point. */
	public static final int GRAY32_INT = 3;
	
	/** 32-bit floating-point. */
	public static final int GRAY32_FLOAT = 4;
	
	/** 8-bit unsigned integer with color lookup table. */
	public static final int COLOR8 = 5;
	
	/** 24-bit interleaved RGB. Import/export only. */
	public static final int RGB = 6;	
	
	/** 24-bit planer RGB. Import only. */
	public static final int RGB_PLANAR = 7;
	
	/** 1-bit black and white. Import only. */
	public static final int BITMAP = 8;
	
	/** 32-bit interleaved ARGB. Import only. */
	public static final int ARGB = 9;
	
	/** 24-bit interleaved BGR. Import only. */
	public static final int BGR = 10;
	
	/**	32-bit unsigned integer. Imported 32-bit integer images are
		converted to floating-point. */
	public static final int GRAY32_UNSIGNED = 11;
	
	/** 48-bit interleaved RGB. */
	public static final int RGB48 = 12;	

	/** 12-bit unsigned integer (0-4095). Import only. */
	public static final int GRAY12_UNSIGNED = 13;	

	/** 24-bit unsigned integer. Import only. */
	public static final int GRAY24_UNSIGNED = 14;	

	/** 32-bit interleaved BARG (MCID). Import only. */
	public static final int BARG  = 15;	

	/** 64-bit floating-point. Import only.*/
	public static final int GRAY64_FLOAT  = 16;	

	/** 48-bit planar RGB. Import only. */
	public static final int RGB48_PLANAR = 17;	

	/** 32-bit interleaved ABGR. Import only. */
	public static final int ABGR = 18;

	/** 32-bit interleaved CMYK. Import only. */
	public static final int CMYK = 19;

	// File formats
	public static final int UNKNOWN = 0;
	public static final int RAW = 1;
	public static final int TIFF = 2;
	public static final int GIF_OR_JPG = 3;
	public static final int FITS = 4;
	public static final int BMP = 5;
	public static final int DICOM = 6;
	public static final int ZIP_ARCHIVE = 7;
	public static final int PGM = 8;
	public static final int IMAGEIO = 9;

	// Compression modes
	public static final int COMPRESSION_UNKNOWN = 0;
	public static final int COMPRESSION_NONE= 1;
	public static final int LZW = 2;
	public static final int LZW_WITH_DIFFERENCING = 3;
	public static final int JPEG = 4;
	public static final int PACK_BITS = 5;
	public static final int ZIP = 6;

}
