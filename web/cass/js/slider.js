$(window).load( 
            function(){
                valor = $('#slsvelocidad').val();
                $('#slsvelocidad').slider({min: 1,
                                                                    max: 5,										
                                                                    step: 1,
                                                                    orientation: 'horizontal',
                                                                    value: valor,
                                                                    selection: 'before',
                                                                    tooltip: 'show',
                                                                    handle: 'round'}
                                                                    ).on('slide',sliderAdapter);
            sliderAdapter();						
            }			
        );

    	function sliderAdapter(){
		velocidad = $('#slsvelocidad').val();
		var txtNumber=["","1", "2", "3", "4", "5"];
		$('#speedtxt').empty();
		$('#speedtxt').html(txtNumber[velocidad<5?velocidad:5] + ' copies');
	
	}