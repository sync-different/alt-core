	$(window).load(slideShow);
	$(window).load( 
			function(){
				
				$(window).bind("resize",slideshowSizeImage);
				
				$('#slsvelocidad').slider(
										{min: 1,
										max: 20,
										
										step: 1,
										orientation: 'horizontal',
										value: 5,
										selection: 'before',
										tooltip: 'show',
										handle: 'round'}
										).on('slide',sliderAdapter); 
				sliderAdapter();	
				slideshowAuto();				
			}
			
		);
	 	
	var timerAuto=0;
	var velocidad=5;
	function sliderAdapter(){
		velocidad = $('#slsvelocidad').val();
		slideshowAuto( );
		slideshowAuto( );
		var txtNumber=["","one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",  "seventeen","eighteen", "nineteen", "twenty"];
		$('#speedtxt').empty();
		$('#speedtxt').html(txtNumber[velocidad<20?velocidad:20]+' speed seconds');
	
	}
	function slideshowAuto( ){
	
		if(timerAuto==0){
			timerAuto=setInterval(
				function(){
					
						$('#sldnext').click();
				},1000*velocidad);
				
		}else{
			clearInterval(timerAuto);
			timerAuto=0;
		}
	}
		
	function slideshowSizeImage(){
			var winHeight=(($(window).height()<window.innerHeight?window.innerHeight:$(window).height()))*0.9-$('#slideshowAuto').height();
			var winWidth= ($(window).width()<window.innerWidth?window.innerWidth:$(window).width())*0.9;
			var winHeightSlide=(($(window).height()<window.innerHeight?window.innerHeight:$(window).height()))-$('#slideshowAuto').height();
			var winWidthSlide= ($(window).width()<window.innerWidth?window.innerWidth:$(window).width());
			$('.slides.li,#slideshow').css({"height":winHeightSlide});
			$('.slides.li,#slideshow').css({"width":winWidthSlide});
			$('img[carrousel=1]').each(
				function(){
					var ratioHeight=1;
					var ratioWidth=1;
					var pHeight=$(this).attr('originalHeight');
					var pWidth=$(this).attr('originalWidth');
					
					if(pHeight>winHeight){
						//pHeight - 100%
						//winHeight - x%
						//x=winHeight*100/pHeight
						ratioHeight=winHeight/pHeight;
					}
					if(pWidth>winWidth){
						//pHeight - 100%
						//winHeight - x%
						//x=winHeight*100/pHeight
						ratioWidth=winWidth/pWidth;
					}
					var ratio = Math.min(ratioHeight,ratioWidth);
					var imgHeigh=pHeight*ratio;
					var imgWidth=pWidth*ratio;
					
					$(this).attr("height",imgHeigh);
					$(this).attr("width", imgWidth);
					
				}
			);
			
			
	}
function slideShow(){
	
	 slideshowSizeImage();
	 
	// We are listening to the window.load event, so we can be sure
	// that the images in the slideshow are loaded properly.


	// Testing wether the current browser supports the canvas element:
	var supportCanvas = 'getContext' in document.createElement('canvas');

	// The canvas manipulations of the images are CPU intensive,
	// this is why we are using setTimeout to make them asynchronous
	// and improve the responsiveness of the page.

	var slides = $('#slideshow li'),
		current = 0,
		slideshow = {width:0,height:0};

	setTimeout(function(){
		
		window.console && window.console.time && console.time('Generated In');
		
		if(supportCanvas){
			$('#slideshow img').each(function(){

				if(!slideshow.width){
					// Taking the dimensions of the first image:
					
					slideshow.width = this.width;
					slideshow.height = this.height;
				}
				
				// Rendering the modified versions of the images:
				createCanvasOverlay(this);
			});
			
			//slideshowSizeImage();
				
			//$(window).bind("resize",slideshowSizeImage);
		}
		
		window.console && window.console.timeEnd && console.timeEnd('Generated In');
		
		$('#slideshow .arrow').click(function(){
			var li			= slides.eq(current),
				canvas		= li.find('canvas'),
				nextIndex	= 0;

			// Depending on whether this is the next or previous
			// arrow, calculate the index of the next slide accordingly.
			
			if($(this).hasClass('next')){
				nextIndex = current >= slides.length-1 ? 0 : current+1;
			}
			else {
				nextIndex = current <= 0 ? slides.length-1 : current-1;
			}

			var next = slides.eq(nextIndex);
			
			if(supportCanvas){

				// This browser supports canvas, fade it into view:
				 
				canvas.fadeIn(function(){
					
					// Show the next slide below the current one:
					next.show();
					current = nextIndex;
					
					// Fade the current slide out of view:
					li.fadeOut(function(){
						li.removeClass('slideActive');
						canvas.hide();
						next.addClass('slideActive');
					});
				});
			}
			else {
				
				// This browser does not support canvas.
				// Use the plain version of the slideshow.
				
				current=nextIndex;
				next.addClass('slideActive').show();
				li.removeClass('slideActive').hide();
			}
		});
		
	},100); 

	}

	// This function takes an image and renders
	// a version of it similar to the Overlay blending
	// mode in Photoshop.
	
	function createCanvasOverlay(image){
		var canvas			= document.createElement('canvas'),
		canvasContext	= canvas.getContext("2d");
		
		// Make it the same size as the image
		 
			
		canvas.width = slideshow.width>0?slideshow.width:image.width;
		canvas.height =slideshow.height>0?slideshow.height:image.height ;
		
		// Drawing the default version of the image on the canvas:
		
				
		canvasContext.drawImage(image,0,0);
	// Taking the image data and storing it in the imageData array:
		var imageData	= canvasContext.getImageData(0,0,canvas.width,canvas.height),
	
		data= imageData.data;
		
		// Loop through all the pixels in the imageData array, and modify
		// the red, green, and blue color values.
		
		for(var i = 0,z=data.length;i<z;i++){
			
			// The values for red, green and blue are consecutive elements
			// in the imageData array. We modify the three of them at once:
			
			data[i] = ((data[i] < 128) ? (2*data[i]*data[i] / 255) : (255 - 2 * (255 - data[i]) * (255 - data[i]) / 255));
			data[++i] = ((data[i] < 128) ? (2*data[i]*data[i] / 255) : (255 - 2 * (255 - data[i]) * (255 - data[i]) / 255));
			data[++i] = ((data[i] < 128) ? (2*data[i]*data[i] / 255) : (255 - 2 * (255 - data[i]) * (255 - data[i]) / 255));
			
			// After the RGB elements is the alpha value, but we leave it the same.
			++i;
		}
		 
		// Putting the modified imageData back to the canvas.
		canvasContext.putImageData(imageData,0,0);
		
		// Inserting the canvas in the DOM, before the image:
		image.parentNode.insertBefore(canvas,image);
			
	}
