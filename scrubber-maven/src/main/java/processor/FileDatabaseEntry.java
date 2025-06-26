/**
 *
 * @author Alejandro Goyen
 * Copyright 2013 Alterante LLC
 * 
 * CONFIDENTIAL AND PROPRIETARY - Property of Alterante LLC
 */


package processor;

import java.io.Serializable;

/**
 * Database entry [Hash, Entry]
 */
public class FileDatabaseEntry implements Serializable {
    private static final long serialVersionUID = 7526471155622776147L;
    public Long mDateModified;
    public String mMD5;

    public FileDatabaseEntry(Long _modified, String _md5) {
        mDateModified = _modified;
        mMD5 = _md5;
    }
}
