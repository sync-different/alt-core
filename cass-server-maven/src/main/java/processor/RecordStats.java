/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */


package processor;

/**
 * Maintain server's worker statistics
 */
public class RecordStats {
    public long nFiles = 0;
    public long nInsert = 0;
    public long nInsertAutoComplete = 0;
    public long nInsertHash = 0;
    public long nRegCount = 0;
    public long nSkipSubStrings = 0;
    public long nSkipSubFolders = 0;
    public long nRetry = 0;
    public long nDeleted = 0;

    /**
     * To String
     * @return
     */
    @Override
    public String toString(){
        String res = "\t-----------------\n";
        res += "\t #Files:  " + nFiles + "\n";
        res += "\t #nInsert:  " + nInsert + "\n";
        res += "\t #nInsertAutoComplete:  " + nInsertAutoComplete + "\n";
        res += "\t #nInsertHash:  " + nInsertHash  + "\n";
        res += "\t #nRegCount:  " + nRegCount + "\n";
        res += "\t #nSkipSubStrings:  " + nSkipSubStrings + "\n";
        res += "\t #nSkipSubSolders:  " + nSkipSubFolders + "\n";
        res += "\t #Errors/Retries: " + nRetry + "\n";
        res += "\t #nDeleted: " + nDeleted + "\n";
        return res;
    }
}
