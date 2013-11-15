package org.red5.xuggler;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 * 
 * Copyright (c) 2006-2008 by respective authors (see below). All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU Lesser General Public License as published by the Free Software 
 * Foundation; either version 2.1 of the License, or (at your option) any later 
 * version. 
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along 
 * with this library; if not, write to the Free Software Foundation, Inc., 
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA 
 */

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.IVideoResampler;
import com.xuggle.xuggler.IPixelFormat.Type;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;

/**
 * Resamples video images using AWT.
 * 
 * @author Paul Gregoire
 */
public class JavaVideoResampler {

	protected Logger log = Red5LoggerFactory.getLogger(this.getClass());
	
	private int outWidth;
	private int outHeight;
	private Type outFormat = IPixelFormat.Type.YUV420P;
	
	private int inWidth;
	private int inHeight;
	private Type inFormat = IPixelFormat.Type.YUV420P;
	
	private IVideoResampler resampler;
	private IVideoPicture outFrame;
	
	public void init() {		
		resampler = IVideoResampler.make(inWidth, inHeight, IPixelFormat.Type.BGR24, inWidth, inHeight, inFormat);
		//outgoing frame
		outFrame = IVideoPicture.make(outFormat, outWidth, outHeight);
	}

	public int getInputWidth() {
		return inWidth;
	}

	public int getInputHeight() {
		return inHeight;
	}

	public Type getInputPixelFormat() {
		return inFormat;
	}
	
	public int getOutputWidth() {
		return outWidth;
	}
	
	public int getOutputHeight() {
		return outHeight;
	}

	public Type getOutputPixelFormat() {
		return outFormat;
	}

	public void setOutWidth(int outWidth) {
		this.outWidth = outWidth;
	}

	public void setOutHeight(int outHeight) {
		this.outHeight = outHeight;
	}

	public void setOutFormat(Type outFormat) {
		this.outFormat = outFormat;
	}

	public void setInWidth(int inWidth) {
		this.inWidth = inWidth;
	}

	public void setInHeight(int inHeight) {
		this.inHeight = inHeight;
	}

	public void setInFormat(Type inFormat) {
		this.inFormat = inFormat;
	}
	
	public IVideoPicture resize(IVideoPicture inFrame) {
        log.debug("Input video picture {}x{}", inFrame.getWidth(), inFrame.getHeight());

		//create bgr picture holder
		IVideoPicture bgrPict = IVideoPicture.make(IPixelFormat.Type.BGR24, inWidth, inHeight);
        
        //convert the input to bgr at the same input size - no resize		
		int ret = resampler.resample(bgrPict, inFrame);
		if (ret < 0) {
			log.error("Error resampling image: {}", ret);
			return null;
		}
        
        //converter to turn xuggle picture into a buffered image
        IConverter bgrConverter = ConverterFactory.createConverter(ConverterFactory.XUGGLER_BGR_24, bgrPict);
        
		//convert into a buffered image
        BufferedImage img = bgrConverter.toImage(bgrPict);
		log.debug("Buffered image created");

		//resize the buffered image
		BufferedImage out = new BufferedImage(outWidth, outHeight, img.getType());  
        Graphics2D g = out.createGraphics();  
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        g.drawImage(img, 0, 0, outWidth, outHeight, 0, 0, inWidth, inHeight, null);  
        g.dispose();  

        log.debug("Output image {}x{}", out.getWidth(), out.getHeight());
        
        //convert the buffered image back into xuggle picture
		BufferedImage worksWithXugglerBufferedImage = convertToType(out, BufferedImage.TYPE_3BYTE_BGR);
		IConverter yuvConverter = ConverterFactory.createConverter(worksWithXugglerBufferedImage, outFormat);	
        outFrame = yuvConverter.toPicture(out, inFrame.getTimeStamp()); 
        outFrame.setQuality(0); 
        outFrame.setKeyFrame(inFrame.isKeyFrame());
                
        log.debug("Output video picture {}x{}", outFrame.getWidth(), outFrame.getHeight());
        log.trace("Output video picture complete: {}", outFrame.isComplete());
        
        bgrPict.delete();
        
		return outFrame;
	}

	public static BufferedImage convertToType(BufferedImage sourceImage, int targetType) {
		BufferedImage image;
		// if the source image is already the target type, return the source
		// image
		if (sourceImage.getType() == targetType) {
			image = sourceImage;
		} else {
			// otherwise create a new image of the target type and draw the new
			// image
			image = new BufferedImage(sourceImage.getWidth(), sourceImage
					.getHeight(), targetType);
			Graphics g = image.getGraphics();
			g.drawImage(sourceImage, 0, 0, null);
			g.dispose();
		}
		return image;
	}	
	
	//use a builder
	public static JavaVideoResampler make(int outWidth, int outHeight, Type outFormat, int inWidth, int inHeight, Type inFormat) {
		JavaVideoResampler resampler = new JavaVideoResampler();
		resampler.setInFormat(inFormat);
		resampler.setInHeight(inHeight);
		resampler.setInWidth(inWidth);
		resampler.setOutFormat(outFormat);
		resampler.setOutHeight(outHeight);
		resampler.setOutWidth(outWidth);		
		return resampler;
	}

	public void destroy() {
		if (resampler != null) {
			resampler.delete();
			resampler = null;
		}
		if (outFrame != null) {
			outFrame.delete();
			outFrame = null;
		}		
	}
		
}
