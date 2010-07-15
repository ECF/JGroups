// $Id: MagicNumberReader.java,v 1.1 2009/07/30 00:58:11 phperret Exp $

package org.jgroups.conf;

/**
 * Reads and maintains mapping between magic numbers and classes
 * @author Filip Hanik (<a href="mailto:filip@filip.net">filip@filip.net)
 * @version 1.0
 */

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jgroups.util.Util;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;

public class MagicNumberReader {
    public static final String MAGIC_NUMBER_FILE="jg-magic-map.xml";

    public String mMagicNumberFile=MAGIC_NUMBER_FILE;

    protected static final Log log=LogFactory.getLog(MagicNumberReader.class);

    public void setFilename(String file) {
        mMagicNumberFile=file;
    }

    /**
     * try to read the magic number configuration file as a Resource form the classpath using getResourceAsStream
     * if this fails this method tries to read the configuration file from mMagicNumberFile using a FileInputStream (not in classpath but somewhere else in the disk)
     *
     * @return an array of ClassMap objects that where parsed from the file (if found) or an empty array if file not found or had en exception
     */
    public ClassMap[] readMagicNumberMapping() {
        try {
            InputStream stream=Util.getResourceAsStream(mMagicNumberFile, this.getClass());
            // try to load the map from file even if it is not a Resource in the class path
            if(stream == null) {
                try {
                    if(log.isTraceEnabled())
                        log.trace("Could not read " + mMagicNumberFile + " as Resource from the CLASSPATH, will try to read it from file.");
                    stream=new FileInputStream(mMagicNumberFile);
                    if(stream != null && log.isTraceEnabled())
                        log.trace("Magic number File found at '" + mMagicNumberFile + '\'');
                }
                catch(FileNotFoundException fnfe) {
                    if(log.isWarnEnabled())
                        log.warn("Failed reading - '" + mMagicNumberFile + "' is not found, got error '" +
                                 fnfe.getLocalizedMessage() + "'. Please make sure it is in the CLASSPATH or in the " +
                                 "specified location. Will continue, but marshalling will be slower");
                }
            }

            if(stream == null) {
                return new ClassMap[0];
            }
            return parse(stream);
        }
        catch(Exception x) {
            if(log.isErrorEnabled()) log.error("failed reading mapig map", x);
        }
        return new ClassMap[0];
    }

    protected static ClassMap[] parse(InputStream stream) throws Exception {
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setValidating(false); //for now
        DocumentBuilder builder=factory.newDocumentBuilder();
        Document document=builder.parse(stream);
        NodeList class_list=document.getElementsByTagName("class");
        java.util.Vector v=new java.util.Vector();
        for(int i=0; i < class_list.getLength(); i++) {
            if(class_list.item(i).getNodeType() == Node.ELEMENT_NODE) {
                v.addElement(parseClassData(class_list.item(i)));
            }
        }
        ClassMap[] data=new ClassMap[v.size()];
        v.copyInto(data);
        return data;
    }//parse

    protected static ClassMap parseClassData(Node protocol) throws java.io.IOException {
        try {
            protocol.normalize();
            int pos=0;
            NodeList children=protocol.getChildNodes();
            /**
             * there should be 4 Element Nodes if we are not overriding
             * 1. description
             * 2. class-name
             * 3. preload
             * 4. magic-number
             */

            String clazzname=null;
            String desc=null;
            String preload=null;
            String magicnumber=null;

            for(int i=0; i < children.getLength(); i++) {
                if(children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    pos++;
                    switch(pos) {
                        case 1:
                            desc=children.item(i).getFirstChild().getNodeValue();
                            break;
                        case 2:
                            clazzname=children.item(i).getFirstChild().getNodeValue();
                            break;
                        case 3:
                            preload=children.item(i).getFirstChild().getNodeValue();
                            break;
                        case 4:
                            magicnumber=children.item(i).getFirstChild().getNodeValue();
                            break;
                    }
                }
            }

            return new ClassMap(clazzname, desc, Boolean.valueOf(preload).booleanValue(), Short.valueOf(magicnumber).shortValue());
        }
        catch(Exception x) {
            IOException tmp=new IOException();
            tmp.initCause(x);
            throw tmp;
        }
    }


}
