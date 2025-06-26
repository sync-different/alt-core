/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */


package services;

import processor.FileDatabase;

public class ScrubService {
    
    private final FileDatabase   mFileRecords;  /*Database of previously process files*/
    public static final String RECORDS_FILE         = "./data/.records.db";

    public ScrubService(String _datapath, String _uuid, String _hostname) {
        mFileRecords = new FileDatabase(_datapath);
        System.out.println("count start = " + mFileRecords.count());
        mFileRecords.sync(_hostname,_uuid);
        System.out.println("count after = " + mFileRecords.count());
        boolean res = mFileRecords.save();
        System.out.println("res= " + res);
    }

    
}
