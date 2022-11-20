package ij.gui;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Panel;

import ij.ImagePlus;

/** This class is used by GenericDialog to add images to dialogs. */
public class ImagePanel extends Panel {
	private ImagePlus img;
	private int width, height;
	 
	ImagePanel(ImagePlus img) {
		this.img = img;
		width = img.getWidth();
		height = img.getHeight();
	}

	public Dimension getPreferredSize() {
		return new Dimension(width, height);
	}

	public Dimension getMinimumSize() {
		return new Dimension(width, height);
	}

	public void paint(Graphics g) {
		g.drawImage(img.getProcessor().createImage(), 0, 0, null);
	}

}
