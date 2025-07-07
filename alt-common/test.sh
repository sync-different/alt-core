java -cp ./target/alt-common-1.0-SNAPSHOT.jar com.alterante.utils.BinaryExtractorUtil ./testfiles/upload.SampleVideo_1280x720_30mb.mp4.4.1.pr part1.dat
java -cp ./target/alt-common-1.0-SNAPSHOT.jar com.alterante.utils.BinaryExtractorUtil ./testfiles/upload.SampleVideo_1280x720_30mb.mp4.4.2.pr part2.dat
java -cp ./target/alt-common-1.0-SNAPSHOT.jar com.alterante.utils.BinaryExtractorUtil ./testfiles/upload.SampleVideo_1280x720_30mb.mp4.4.3.pr part3.dat
java -cp ./target/alt-common-1.0-SNAPSHOT.jar com.alterante.utils.BinaryExtractorUtil ./testfiles/upload.SampleVideo_1280x720_30mb.mp4.4.4.pr part4.dat
cat part1.dat part2.dat part3.dat part4.dat >full.mp4
open ./full.mp4

