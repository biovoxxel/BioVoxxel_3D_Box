package de.biovoxxel.bv3dbox.plugins;

import java.awt.Point;

import org.scijava.Cancelable;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.log.StderrLogService;
import org.scijava.prefs.DefaultPrefService;
import org.scijava.prefs.PrefService;

import de.biovoxxel.bv3dbox.utilities.BV3DBoxSettings;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.measure.Calibration;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import net.haesleinhuepf.clij2.CLIJ2;


public class BVPseudoFlatFieldCorrection implements Cancelable {

	PrefService prefs = new DefaultPrefService();
	LogService log = new StderrLogService();
			
	CLIJ2 clij2;
	ClearCLBuffer inputImage;
	
	private ImagePlus inputImagePlus;
	private ImagePlus outputImagePlus = null;
	private String outputImageName = "";
	private double x_y_ratio;
	private double z_x_ratio;

		
		
	public BVPseudoFlatFieldCorrection(ImagePlus inputImagePlus) {
		
		setInputImage(inputImagePlus);
		
	}
	
	
	public void setInputImage(ImagePlus image) {
		
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "debug_level", LogLevel.INFO));
		this.inputImagePlus = image;
				
		outputImageName = WindowManager.getUniqueName("PFFC_" + inputImagePlus.getTitle());
		log.debug("outputImageName = " + outputImageName);
		
		readCalibration();
		
		clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		if (inputImagePlus.getRoi() != null) {
			inputImage = clij2.pushCurrentSelection(inputImagePlus);
		} else {
			inputImage = clij2.push(inputImagePlus);			
		}
		
	}
		
	
	
	public void runCorrection(float radius, boolean force2D, boolean showBackgroundImage) {
		
		ClearCLBuffer backgound = clij2.create(inputImage.getDimensions(), NativeTypeEnum.Float);
		clij2.copy(inputImage, backgound);
		ClearCLBuffer blurredBackground = clij2.create(backgound);
		
		double y_filter_radius = radius * x_y_ratio;
		
		log.debug("filterRadius=" + radius);
		log.debug("y_filter_radius=" + y_filter_radius);
		
						
		if (inputImagePlus.isStack()) {
			int frames = inputImagePlus.getNFrames();
			int z_slices = inputImagePlus.getNSlices();
			log.debug("frames=" + frames);
			log.debug("z_slices=" + z_slices);
			
			double z_filter_radius = 0; 
			if (z_slices > 1 && frames == 1 && !force2D) {
				z_filter_radius = radius / z_x_ratio;				
			} 
			log.debug("z_filter_radius=" + z_filter_radius);
			
			clij2.gaussianBlur3D(backgound, blurredBackground, radius, y_filter_radius, z_filter_radius);
			log.debug("3D filtering for background creation");
		} else {
			clij2.gaussianBlur2D(backgound, blurredBackground, radius, y_filter_radius);
			log.debug("2D filtering for background creation");
		}
		
		backgound.close();
		
		ImagePlus tempOutputImagePlus;
		if (showBackgroundImage) {
			tempOutputImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, blurredBackground, false, LutNames.PHYSICS);
			
		} else {
			
			double meanBackgroundIntensity = clij2.meanOfAllPixels(blurredBackground);
			log.debug("meanBackgroundIntensity = " + meanBackgroundIntensity);
			
			ClearCLBuffer dividedImage = clij2.create(blurredBackground);
			clij2.divideImages(inputImage, blurredBackground, dividedImage);
			log.debug("Image devided by background");
			
			ClearCLBuffer outputImage = clij2.create(dividedImage);
			clij2.multiplyImageAndScalar(dividedImage, outputImage, meanBackgroundIntensity);
			dividedImage.close();
			
			tempOutputImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, outputImage, true, LutNames.GRAY);
			outputImage.close();
		}
		
		blurredBackground.close();
		
		
		outputImagePlus = WindowManager.getImage(outputImageName);
		
		if (outputImagePlus == null) {
			outputImagePlus = new ImagePlus();
		}
		
		outputImagePlus.setImage(tempOutputImagePlus);
		outputImagePlus.setTitle(outputImageName);
		ImageWindow inputImageWindow = inputImagePlus.getWindow();
		Point inputImageLocation = inputImageWindow.getLocationOnScreen();
		outputImagePlus.show();
		outputImagePlus.getWindow().setLocation(inputImageLocation.x + inputImageWindow.getWidth() + 10, inputImageLocation.y);
		
	}
	
	
	
	
	
	private void readCalibration() {
		Calibration cal = inputImagePlus.getCalibration();
		x_y_ratio = cal.pixelWidth / cal.pixelHeight;
		z_x_ratio = cal.pixelDepth / cal.pixelWidth;
			
	}
	
		
	
	public String getOutputImageName() {
		return outputImageName;
	}
	

	@Override
	public boolean isCanceled() {
		// TODO Auto-generated method stub
		return false;
	}


	@Override
	public void cancel(String reason) {
		ImagePlus outputImagePlus = WindowManager.getImage(outputImageName);
		if (outputImagePlus != null) {
			outputImagePlus.close();
		}
		clij2.close();
		
	}


	@Override
	public String getCancelReason() {
		// TODO Auto-generated method stub
		return null;
	}
}







