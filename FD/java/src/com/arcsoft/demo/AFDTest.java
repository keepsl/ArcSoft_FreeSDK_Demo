package com.arcsoft.demo;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.arcsoft.AFD_FSDKLibrary;
import com.arcsoft.AFD_FSDK_FACERES;
import com.arcsoft.AFD_FSDK_Version;
import com.arcsoft.ASVLOFFSCREEN;
import com.arcsoft.ASVL_COLOR_FORMAT;
import com.arcsoft.CLibrary;
import com.arcsoft.MRECT;
import com.arcsoft._AFD_FSDK_OrientPriority;
import com.arcsoft.utils.ImageLoader;
import com.arcsoft.utils.ImageLoader.BufferInfo;
import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;


public class AFDTest {
	public static final String APPID     = "XXXXXXXXXXXXXXX";
	public static final String FD_SDKKEY = "YYYYYYYYYYYYYYY";
	
	public static final int FD_WORKBUF_SIZE = 20*1024*1024;
	public static final int MAX_FACE_NUM = 50;
	
	public static final boolean bUseYUVFile = false;
	
    public static void main(String[] args) {
		System.out.println("#####################################################");
		
	    //Init Engine
    	Pointer pFDWorkMem = CLibrary.INSTANCE.malloc(FD_WORKBUF_SIZE);
        
        PointerByReference phFDEngine = new PointerByReference();
        NativeLong ret = AFD_FSDKLibrary.INSTANCE.AFD_FSDK_InitialFaceEngine(
				        		APPID, FD_SDKKEY, pFDWorkMem, FD_WORKBUF_SIZE, 
				        		phFDEngine, _AFD_FSDK_OrientPriority.AFD_FSDK_OPF_0_HIGHER_EXT,
			                    16, MAX_FACE_NUM);
        if (ret.intValue() != 0) {
			 CLibrary.INSTANCE.free(pFDWorkMem);
        	 System.out.println("AFD_FSDK_InitialFaceEngine ret == "+ret);
        	 System.exit(0);
        }
        
		//print FDEngine version
        Pointer hFDEngine = phFDEngine.getValue();
        AFD_FSDK_Version versionFD = AFD_FSDKLibrary.INSTANCE.AFD_FSDK_GetVersion(hFDEngine);
        System.out.println(String.format("%d %d %d %d", versionFD.lCodebase, versionFD.lMajor, versionFD.lMinor,versionFD.lBuild));
        System.out.println(versionFD.Version);
        System.out.println(versionFD.BuildDate);
        System.out.println(versionFD.CopyRight);
        
       	//load Image Data
    	ASVLOFFSCREEN inputImg;
    	if(bUseYUVFile){
	        String filePath = "001_640x480_I420.YUV";
	        int yuv_width = 640;
	        int yuv_height = 480;
	        int yuv_format = ASVL_COLOR_FORMAT.ASVL_PAF_I420;
	        
	        inputImg = loadYUVImage(filePath,yuv_width,yuv_height,yuv_format);
        }else{
        	String filePath = "003.jpg";
        	
        	inputImg = loadImage(filePath);
        }
        
    	//Do Face Detect
      	FaceInfo[] faceInfos = doFaceDetection(hFDEngine,inputImg);
      	for (int i = 0; i < faceInfos.length; i++) {
      		FaceInfo rect = faceInfos[i];
      		System.out.println(String.format("%d (%d %d %d %d) orient %d",i,rect.left, rect.top,rect.right,rect.bottom,rect.orient));
		}

        //Release Engine
        AFD_FSDKLibrary.INSTANCE.AFD_FSDK_UninitialFaceEngine(hFDEngine);
        
    	CLibrary.INSTANCE.free(pFDWorkMem);
    	
    	System.out.println("#####################################################");
    }

    public static FaceInfo[] doFaceDetection(Pointer hFDEngine,ASVLOFFSCREEN inputImg){
    	FaceInfo[] faceInfo = new FaceInfo[0];
    	
    	PointerByReference ppFaceRes = new PointerByReference();
    	NativeLong ret = AFD_FSDKLibrary.INSTANCE.AFD_FSDK_StillImageFaceDetection(hFDEngine,inputImg,ppFaceRes);
        if (ret.intValue() != 0) {
       	    System.out.println("AFD_FSDK_StillImageFaceDetection ret == "+ret);
       	    return faceInfo;
        }
        
        AFD_FSDK_FACERES faceRes = new AFD_FSDK_FACERES(ppFaceRes.getValue());
        if(faceRes.nFace>0){
        	faceInfo = new FaceInfo[faceRes.nFace];
	        for (int i = 0; i < faceRes.nFace; i++) {
	        	MRECT rect = new MRECT(new Pointer(Pointer.nativeValue(faceRes.rcFace.getPointer())+faceRes.rcFace.size()*i));
	        	int orient = faceRes.lfaceOrient.getPointer().getInt(i*4);
	        	faceInfo[i] = new FaceInfo();
	        	
	        	faceInfo[i].left = rect.left;
	        	faceInfo[i].top = rect.top;
	        	faceInfo[i].right = rect.right;
	        	faceInfo[i].bottom = rect.bottom;
	        	faceInfo[i].orient = orient;
			}
        }
	    return faceInfo;
    }
	
	public static ASVLOFFSCREEN loadYUVImage(String yuv_filePath,int yuv_width,int yuv_height,int yuv_format) {
        int yuv_rawdata_size = 0;
        
        ASVLOFFSCREEN  inputImg = new ASVLOFFSCREEN();
        inputImg.u32PixelArrayFormat = yuv_format;
        inputImg.i32Width = yuv_width;
        inputImg.i32Height = yuv_height;
        if (ASVL_COLOR_FORMAT.ASVL_PAF_I420 == inputImg.u32PixelArrayFormat) {
            inputImg.pi32Pitch[0] = inputImg.i32Width;
            inputImg.pi32Pitch[1] = inputImg.i32Width/2;
            inputImg.pi32Pitch[2] = inputImg.i32Width/2;
            yuv_rawdata_size = inputImg.i32Width*inputImg.i32Height*3/2;
        } else if (ASVL_COLOR_FORMAT.ASVL_PAF_NV12 == inputImg.u32PixelArrayFormat) {
            inputImg.pi32Pitch[0] = inputImg.i32Width;
            inputImg.pi32Pitch[1] = inputImg.i32Width;
            yuv_rawdata_size = inputImg.i32Width*inputImg.i32Height*3/2;
        } else if (ASVL_COLOR_FORMAT.ASVL_PAF_NV21 == inputImg.u32PixelArrayFormat) {
            inputImg.pi32Pitch[0] = inputImg.i32Width;
            inputImg.pi32Pitch[1] = inputImg.i32Width;
            yuv_rawdata_size = inputImg.i32Width*inputImg.i32Height*3/2;
        } else if (ASVL_COLOR_FORMAT.ASVL_PAF_YUYV == inputImg.u32PixelArrayFormat) {
            inputImg.pi32Pitch[0] = inputImg.i32Width*2;
            yuv_rawdata_size = inputImg.i32Width*inputImg.i32Height*2;
        }else{
	       	 System.out.println("unsupported  yuv format");
	       	 System.exit(0);
        }
        
        //load YUV Image Data from File
        byte[] imagedata = new byte[yuv_rawdata_size];
        File f = new File(yuv_filePath);
        InputStream ios = null;
        try {
            ios = new FileInputStream(f);
            ios.read(imagedata);
     
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("error in loading yuv file");
       	    System.exit(0);
		} finally {
            try {
                if (ios != null){
                    ios.close();
                }
            } catch (IOException e) {
            }
        }
        
        if (ASVL_COLOR_FORMAT.ASVL_PAF_I420 == inputImg.u32PixelArrayFormat) {
            inputImg.ppu8Plane[0] = new Memory(inputImg.pi32Pitch[0]*inputImg.i32Height);
            inputImg.ppu8Plane[0].write(0, imagedata, 0, inputImg.pi32Pitch[0]*inputImg.i32Height);
            inputImg.ppu8Plane[1] = new Memory(inputImg.pi32Pitch[1]*inputImg.i32Height/2);
            inputImg.ppu8Plane[1].write(0, imagedata, inputImg.pi32Pitch[0]*inputImg.i32Height, inputImg.pi32Pitch[1]*inputImg.i32Height/2);
            inputImg.ppu8Plane[2] = new Memory(inputImg.pi32Pitch[2]*inputImg.i32Height/2);
            inputImg.ppu8Plane[2].write(0, imagedata,inputImg.pi32Pitch[0]*inputImg.i32Height+ inputImg.pi32Pitch[1]*inputImg.i32Height/2, inputImg.pi32Pitch[2]*inputImg.i32Height/2);
            inputImg.ppu8Plane[3] = Pointer.NULL;
        } else if (ASVL_COLOR_FORMAT.ASVL_PAF_NV12 == inputImg.u32PixelArrayFormat) {
            inputImg.ppu8Plane[0] = new Memory(inputImg.pi32Pitch[0]*inputImg.i32Height);
            inputImg.ppu8Plane[0].write(0, imagedata, 0, inputImg.pi32Pitch[0]*inputImg.i32Height);
            inputImg.ppu8Plane[1] = new Memory(inputImg.pi32Pitch[1]*inputImg.i32Height/2);
            inputImg.ppu8Plane[1].write(0, imagedata, inputImg.pi32Pitch[0]*inputImg.i32Height, inputImg.pi32Pitch[1]*inputImg.i32Height/2);
            inputImg.ppu8Plane[2] = Pointer.NULL;
            inputImg.ppu8Plane[3] = Pointer.NULL;
        } else if (ASVL_COLOR_FORMAT.ASVL_PAF_NV21 == inputImg.u32PixelArrayFormat) {
            inputImg.ppu8Plane[0] = new Memory(inputImg.pi32Pitch[0]*inputImg.i32Height);
            inputImg.ppu8Plane[0].write(0, imagedata, 0, inputImg.pi32Pitch[0]*inputImg.i32Height);
            inputImg.ppu8Plane[1] = new Memory(inputImg.pi32Pitch[1]*inputImg.i32Height/2);
            inputImg.ppu8Plane[1].write(0, imagedata, inputImg.pi32Pitch[0]*inputImg.i32Height, inputImg.pi32Pitch[1]*inputImg.i32Height/2);
            inputImg.ppu8Plane[2] = Pointer.NULL;
            inputImg.ppu8Plane[3] = Pointer.NULL;
        } else if (ASVL_COLOR_FORMAT.ASVL_PAF_YUYV == inputImg.u32PixelArrayFormat) {
            inputImg.ppu8Plane[0] = new Memory(inputImg.pi32Pitch[0]*inputImg.i32Height);
            inputImg.ppu8Plane[0].write(0, imagedata, 0, inputImg.pi32Pitch[0]*inputImg.i32Height);
            inputImg.ppu8Plane[1] = Pointer.NULL;
            inputImg.ppu8Plane[2] = Pointer.NULL;
            inputImg.ppu8Plane[3] = Pointer.NULL;
        }else{
	       	 System.out.println("unsupported yuv format");
	       	 System.exit(0);
        }

        inputImg.setAutoRead(false);
        return inputImg;
	}
	
	public static ASVLOFFSCREEN loadImage(String filePath) {
	      BufferInfo bufferInfo = ImageLoader.getI420FromFile(filePath);
	      ASVLOFFSCREEN inputImg = new ASVLOFFSCREEN();
	      inputImg.u32PixelArrayFormat = ASVL_COLOR_FORMAT.ASVL_PAF_I420;
	      inputImg.i32Width = bufferInfo.width;
	      inputImg.i32Height = bufferInfo.height;
	      inputImg.pi32Pitch[0] = inputImg.i32Width;
	      inputImg.pi32Pitch[1] = inputImg.i32Width/2;
	      inputImg.pi32Pitch[2] = inputImg.i32Width/2;
	      inputImg.ppu8Plane[0] = new Memory(inputImg.pi32Pitch[0]*inputImg.i32Height);
	      inputImg.ppu8Plane[0].write(0, bufferInfo.base, 0, inputImg.pi32Pitch[0]*inputImg.i32Height);
	      inputImg.ppu8Plane[1] = new Memory(inputImg.pi32Pitch[1]*inputImg.i32Height/2);
	      inputImg.ppu8Plane[1].write(0, bufferInfo.base, inputImg.pi32Pitch[0]*inputImg.i32Height, inputImg.pi32Pitch[1]*inputImg.i32Height/2);
	      inputImg.ppu8Plane[2] = new Memory(inputImg.pi32Pitch[2]*inputImg.i32Height/2);
	      inputImg.ppu8Plane[2].write(0, bufferInfo.base,inputImg.pi32Pitch[0]*inputImg.i32Height+ inputImg.pi32Pitch[1]*inputImg.i32Height/2, inputImg.pi32Pitch[2]*inputImg.i32Height/2);
	      inputImg.ppu8Plane[3] = Pointer.NULL;
	      
	      inputImg.setAutoRead(false);
	      return inputImg;
	}
	
	public static class FaceInfo{
	    public int left;
	    public int top;
	    public int right;
	    public int bottom;
	    public int orient;
	}
}
