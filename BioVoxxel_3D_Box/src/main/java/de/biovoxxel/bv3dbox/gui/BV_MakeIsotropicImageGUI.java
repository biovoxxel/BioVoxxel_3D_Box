package de.biovoxxel.bv3dbox.gui;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.plugins.BV_MakeIsotropicImage;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;

@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Segmentation>Make Isotropic Image")
public class BV_MakeIsotropicImageGUI implements Command {

	
	@Parameter(label = "Image", required = true)
	ImagePlus inputImagePlus;
	
	@Override
	public void run() {
		
		CLIJ2 clij2 = CLIJ2.getInstance();
		BV_MakeIsotropicImage bvmii = new BV_MakeIsotropicImage(clij2, inputImagePlus);
		ClearCLBuffer isotropic_image = bvmii.makeIsotropic(clij2, inputImagePlus);
		
		double pixelSize = inputImagePlus.getCalibration().pixelWidth;
		
		ImagePlus isotropicImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, isotropic_image, false, LutNames.GRAY);
		
		isotropicImagePlus.setTitle("iso_" + WindowManager.getUniqueName(inputImagePlus.getTitle()));
		
		

		Calibration cal = new Calibration();
		cal.pixelWidth = cal.pixelHeight = cal.pixelDepth = pixelSize;
		cal.setUnit(inputImagePlus.getCalibration().getUnit());	
		
		isotropicImagePlus.setCalibration(cal);
		
		isotropicImagePlus.show();
	}

}
