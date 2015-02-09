#pragma version(1)
#pragma rs java_package_name(org.appspot.apprtc)

#include "rs_debug.rsh"
#include "rs_time.rsh"

rs_allocation invocation_time;

int32_t width, height;

rs_allocation yuvInput;


#define STEPS 640
#define STEPSIZE 480

int32_t YWidthNr, YHeightNr;
int32_t UVWidthNr, UVHeightNr;
int32_t YWidth, YHeight;
int32_t tileW, tileH;
int32_t steps;
int32_t stepSize;
int32_t YRealWidth, YRealHeight;

rs_allocation gSrc;
rs_allocation normalYUV;

rs_allocation yPanel;
rs_allocation hist2D;
rs_allocation hist;
rs_allocation maxValue;
rs_allocation stepsAlloc;
rs_allocation stepSizeAlloc;


uchar touchRoof;

void histclr()
{
    int32_t old = rsGetElementAt_int(maxValue, 0);
    rsDebug("total invoke: ", old);
    rsSetElementAt_int(maxValue, 0, 0);
    for(int32_t i = 0; i < 256; i++){
        rsSetElementAt_int(hist, 0, i);
    }	
}

void __attribute__((kernel)) histnew(uchar in, uint32_t x, uint32_t y)
{
    int32_t old = rsGetElementAt_int(hist, in);
    rsSetElementAt_int(hist, old+1, in);

    old = rsGetElementAt_int(maxValue, 0);
    rsSetElementAt_int(maxValue, old+1, 0);

}


void setTouchRoof()
{
	uchar res = 0;
	int32_t pixelSum = 0;
	int32_t n = 0;
	int32_t i;
	for(i = 0; i < 256; i++)
		pixelSum += rsGetElementAt_int(hist, i);
	
	for(i = 255; i >= 0; i--){
		n += rsGetElementAt_int(hist, i);
		if(n * 1.0f / pixelSum > 0.10f){
		//if(n > 0){
			res = i;
			break;
		}
	}	
	touchRoof = 255 - res;
	rsSetElementAt_int(maxValue, res, 0);
}



void __attribute__((kernel)) hist1111(uchar in, uint32_t x, uint32_t y)
{
    for(int32_t i = 0; i < 256; i++){
    	rsSetElementAt_int(hist2D, 0, i, y);
    }
    
//    rsDebug("x, y, steps, stepSize", x, y, steps);
    int32_t _stepSize = rsGetElementAt_int(stepSizeAlloc, 0);
    for(int32_t i = y * _stepSize; i < y * _stepSize + _stepSize; i++){
        uchar Y = rsGetElementAt_uchar(yPanel, i % 480, i / 480);
	int32_t old = rsGetElementAt_int(hist2D, Y, y);
	rsSetElementAt_int(hist2D, old+1, Y, y);
    }
//	for(int32_t i = y * stepSize; i < y * stepSize + stepSize; i++){
//		uchar Y = rsGetElementAt_uchar(yPanel, i % YWidth, i / YWidth);
//		int32_t old = rsGetElementAt_int(hist2D, Y, y);
//		rsSetElementAt_int(hist2D, old+1, Y, y);
//	}

}

void __attribute__((kernel)) hist111(uchar in, uint32_t x, uint32_t y)
{
	for(int32_t i = 0; i < 256; i++){
		rsSetElementAt_int(hist2D, 0, i, y);
	}
	

	for(int32_t i = y * stepSize + 32; i < y * stepSize + 32 + YRealWidth; i++){
		uchar Y = rsGetElementAt_uchar(yPanel, i % YWidth, i / YWidth);
		int32_t old = rsGetElementAt_int(hist2D, Y, y);
		rsSetElementAt_int(hist2D, old+1, Y, y);
	}
}		


void __attribute__((kernel)) hist11(uchar in, uint32_t x, uint32_t y)
{
	for(int32_t i = 0; i < 256; i++){
		rsSetElementAt_int(hist2D, 0, i, y);
	}
	

	for(int32_t i = y * stepSize; i < (y + 1) * stepSize; i++){
		if(i % width > 1280) continue;
		if(i / width > 720) continue;
		uchar Y = rsGetElementAt_int(yuvInput, i % width, i / width);
		int32_t old = rsGetElementAt_int(hist2D, Y, y);
		rsSetElementAt_int(hist2D, old+1, Y, y);
	}
}		

void __attribute__((kernel)) hist1(uchar in, uint32_t x, uint32_t y)
{

	for(int32_t i = 0; i < 256; i++){
		rsSetElementAt_int(hist2D, 0, i, y);
	}
	int32_t rows = (tileH * YWidthNr * YHeightNr) / steps;
	
	
//	for(int32_t i = 0; i < YWidth ; i++){
//		uchar Y = rsGetElementAt_uchar(normalYUV, i, y);
//		int32_t old = rsGetElementAt_int(hist2D, Y, y);
//		rsSetElementAt_int(hist2D, old+1, Y, y);
//	}

	int32_t st_y = y * rows;
	for(int32_t j = 0; j < rows; j++){
		for(int32_t i = 0; i < tileW ; i++){
			uchar Y = rsGetElementAt_uchar(gSrc, i, st_y + j);
			int32_t old = rsGetElementAt_int(hist2D, Y, y);
			rsSetElementAt_int(hist2D, old+1, Y, y);
		}
	}
}

int32_t __attribute__((kernel)) hist2(uint32_t x)
{
	int32_t sum = 0;
	int32_t _steps = rsGetElementAt_int(stepsAlloc, 0);	

	for(int32_t i = 0; i < _steps; i++){
		sum += rsGetElementAt_int(hist2D, x, i);
	}
	return sum; 
}


uchar incr;
uchar __attribute__((kernel)) incrLums(uchar in, uint32_t x, uint32_t y)
{
	if(255 - incr < in){
		return 255;
	}else{
		return in + incr;
	}
	//return in + touchRoof;
}



uchar4 __attribute__((kernel)) convert2(uint32_t x, uint32_t y)
{
	uchar3 color, yuvcolor;
	yuvcolor.x = rsGetElementAt_uchar(normalYUV, x, y);
	yuvcolor.y = rsGetElementAt_uchar(normalYUV, x / 2 * 2, YHeightNr * tileH + y / 2);
	yuvcolor.z = rsGetElementAt_uchar(normalYUV, x / 2 * 2 + 1, YHeightNr * tileH + y / 2 );
	
	color.x = yuvcolor.x + 1.140 *                                ( yuvcolor.z - 128 ) ;
	color.y = yuvcolor.x - 0.395 * ( yuvcolor.y - 128 ) - 0.581 * ( yuvcolor.z - 128 ) ;
	color.z = yuvcolor.x + 2.032 * ( yuvcolor.y - 128 )                                ;
	
//	uchar4 rgba, yuva;
//	yuva.x = rsGetElementAt_uchar(normalYUV, x, y);
//	yuva.y = rsGetElementAt_uchar(normalYUV, x / 2 * 2, YHeightNr * tileH + y / 2);
//	yuva.z = rsGetElementAt_uchar(normalYUV, x / 2 * 2 + 1, YHeightNr * tileH + y / 2 );
	
		
	return (uchar4){color.x, color.y, color.z, 255};
}




void __attribute__((kernel)) convert1(uchar input, uint32_t x, uint32_t y)
{
	int32_t anchor_w, anchor_h;
	int32_t isLastLine = 0;
	int32_t isY;
	uchar val;
	int64_t st, ed, dur;
	
	uint32_t yTileNr = YWidthNr * YHeightNr;

		
	if(y < yTileNr){
		isY = 1;
	}else{
		isY = 0;
		y -= yTileNr;
	}
	
	if(isY == 1){
		if( ((y % (2 * YWidthNr)) + (YWidthNr * YHeightNr - y)) == YWidthNr ){
			isLastLine = 1;
		}
	}else{
		if( ((y % (2 * UVWidthNr)) + (UVWidthNr * UVHeightNr - y)) == UVWidthNr ){
			isLastLine = 1;
		}
	}
	
	anchor_h = 	(y / ( 2 * YWidthNr)) * 2 * tileH;	
	if(isLastLine == 0){
		anchor_w = ((y % ( 2 * YWidthNr)) / 8 ) * 4 * tileW;
		switch(y % 8){
		case 0:
			break;
		case 1:
			anchor_w += tileW;
			break;
		case 2:
			anchor_h += tileH;
			break;
		case 3:
			anchor_w += tileW;
			anchor_h += tileH;
			break;
		case 4:
			anchor_w += 2 * tileW;
			anchor_h += tileH;
			break;
		case 5:
			anchor_w += 3 * tileW;
			anchor_h += tileH;
			break;
		case 6:
			anchor_w += 2 * tileW;
			break;
		case 7:
			anchor_w += 3 * tileW;
			break;
		}
	}else{
		anchor_w = (y % YWidthNr) * tileW;
	}
	

	//rsDebug("tile #, w, h: ", (float)y, (float)anchor_w, (float)anchor_h); 
	//st = rsUptimeNanos();

	//uint32_t val4;
	
	uint32_t YHeightL = YHeightNr * tileH;
	if(isY == 1){
	       /*
		rsAllocationCopy2DRange	(normalYUV, anchor_w, anchor_h, 0,
								 RS_ALLOCATION_CUBEMAP_FACE_POSITIVE_X,
								 tileW, tileH,
								 gSrc, 0, y * tileH, 0,
								 RS_ALLOCATION_CUBEMAP_FACE_POSITIVE_X);
		*/							 
		/*					 
		for(int32_t j = 0; j < tileH; j++){
			for(int32_t i = 0; i < tileW; i++){
				val = rsGetElementAt_uchar(gSrc, j * tileW + i, y);
				rsSetElementAt_uchar(normalYUV, val, anchor_w + i, anchor_h + j);
			}
		}
		*/
	}else{
/*
		rsAllocationCopy2DRange	(normalYUV, anchor_w, anchor_h + YHeightL, 0,
							 	 RS_ALLOCATION_CUBEMAP_FACE_POSITIVE_X,
							 	 tileW, tileH,
								 gSrc, 0, (yTileNr + y) * tileH, 0,
								 RS_ALLOCATION_CUBEMAP_FACE_POSITIVE_X);
*/
		/*
		for(int32_t j = 0; j < tileH; j++){
			for(int32_t i = 0; i < tileW; i++){	
				val = rsGetElementAt_uchar(gSrc, j * tileW + i, y + yTileNr);
				if(i % 2 == 0)
					rsSetElementAt_uchar(normalYUV, val, anchor_w + i + 1, YHeightL + anchor_h + j);
				else
					rsSetElementAt_uchar(normalYUV, val, anchor_w + i - 1, YHeightL + anchor_h + j);
			}
		}
		*/
	}
	//ed = rsUptimeNanos();
	//dur = ed - st;
	//rsDebug("performance: ", dur); 
		
}

void __attribute__((kernel)) convert11(uchar input, uint32_t x, uint32_t y)
{
	int32_t anchor_w_y, anchor_h_y;
	int32_t anchor_w_uv, anchor_h_uv;
	int32_t isLastLine = 0;
	int32_t uv_idx, isPreHalf;		
		
	if( ((y % (2 * YWidthNr)) + (YWidthNr * YHeightNr - y)) == YWidthNr ){
			isLastLine = 1;
	}
		
	anchor_h_y = (y / ( 2 * YWidthNr)) * 2;	
	if(isLastLine == 0){
		anchor_w_y = ((y % ( 2 * YWidthNr)) / 8 ) * 4;
		switch(y % 8){
		case 0:
			break;
		case 1:
			anchor_w_y += 1;
			break;
		case 2:
			anchor_h_y += 1;
			break;
		case 3:
			anchor_w_y += 1;
			anchor_h_y += 1;
			break;
		case 4:
			anchor_w_y += 2;
			anchor_h_y += 1;
			break;
		case 5:
			anchor_w_y += 3;
			anchor_h_y += 1;
			break;
		case 6:
			anchor_w_y += 2;
			break;
		case 7:
			anchor_w_y += 3;
			break;
		}
	}else{
		anchor_w_y = (y % YWidthNr);
	}
	
	anchor_w_uv = anchor_w_y;
	anchor_h_uv = anchor_h_y / 2;
	
	if(anchor_h_y % 2 == 0){
		isPreHalf = 1;
	}else{
		isPreHalf = 0;
	}

	uv_idx = anchor_h_uv / 2 * (YWidthNr * 2);
	if(UVWidthNr * UVHeightNr - uv_idx == UVWidthNr){
		isLastLine = 1;
	}else{
		isLastLine = 0;
	}
	
	
	if(isLastLine == 1){
		uv_idx += anchor_w_uv;
	}else{
		uv_idx += anchor_w_uv / 4 * 8;					
		switch(anchor_h_uv % 2){
		case 0:
			switch(anchor_w_uv % 4){
			case 0:
				break;
			case 1:
				uv_idx += 1;
				break;
			case 2:
				uv_idx += 6;
				break;
			case 3:
				uv_idx += 7;
				break;
			}
			break;
		case 1:
			switch(anchor_w_uv % 4){
			case 0:
				uv_idx += 2;
				break;
			case 1:
				uv_idx += 3;
				break;
			case 2:
				uv_idx += 4;
				break;
			case 3:
				uv_idx += 5;
				break;
			}
			break;
		}
	}

	//rsDebug("tile #, w, h: ", (float)y, (float)anchor_w, (float)anchor_h); 
	anchor_w_y *= tileW;
	anchor_h_y *= tileH;
	uv_idx += (YWidthNr * YHeightNr);
	
	uchar yy, uu, vv;
	int32_t halfPixels = tileH * tileW / 2;
	for(int32_t j = 0; j < tileH; j++){
		for(int32_t i = 0; i < tileW; i++){
			
			yy = rsGetElementAt_uchar(gSrc, j * tileW + i, y);
			if(isPreHalf == 1){
				uu = rsGetElementAt_uchar(gSrc, j / 2 * tileW + i / 2 * 2, uv_idx);
				vv = rsGetElementAt_uchar(gSrc, j / 2 * tileW + i / 2 * 2 + 1, uv_idx);
			}else{
				uu = rsGetElementAt_uchar(gSrc, halfPixels + j / 2 * tileW + i / 2 * 2, uv_idx);
				vv = rsGetElementAt_uchar(gSrc, halfPixels + j / 2 * tileW + i / 2 * 2 + 1, uv_idx);
			}				
			//rsSetElementAt_uchar(normalYUV, val, anchor_w + i, anchor_h + j);
		}
	}
		
}
