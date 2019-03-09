package com.uberboom.pdf;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Set;

import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfFormField;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfContentByte;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
// import joptsimple.OptionException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.*;

import java.io.File;

/**
 * Fill in PDF form fields with the values stored in an XML file and save it to a new PDF
 *
 * @author Bernd Ennsfellner
 */
public class PdfForms {

    /**
     * PDF template with form fields
     */
    private static String pdfTemplate;

    /**
     * XML file with form values (key/value pairs)
     */
    private static String xmlFile;

    /**
     * PDF target file
     */
    private static String pdfTarget;

    /**
     * Verbose mode
     */
    private static boolean verboseMode = false;

    /**
     * Font directory
     */
    private static String fontPath;

    /**
     * Flatten form fields
     */
    private static boolean flatten = false;

    /**
     * Main
     *
     * @param args String[]
     */
    public static void main(String[] args) {
        setOptions(args);

        System.out.println("PDF Template: " + pdfTemplate);
        System.out.println("XML File:     " + xmlFile);
        System.out.println("PDF Target:   " + pdfTarget);
        System.out.println("Fonts Path:   " + fontPath);
        System.out.println("-----------------------");

        if (verboseMode) {
            printFormFields();
        }

        try {
            LinkedList<String> verboseMessages = new LinkedList<String>();

            FileOutputStream outputStream = new FileOutputStream(pdfTarget);

            PdfReader reader = new PdfReader(pdfTemplate);
            PdfStamper stamper = new PdfStamper(reader, outputStream);

            AcroFields form = stamper.getAcroFields();
            // Set<String> fields = form.getFields().keySet();

            BaseFont bfStandard = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            BaseFont bfCustom;

            // fill form fields
            NodeList nList = getXmlNodes();
            for (int temp = 0; temp < nList.getLength(); temp++) {

                Node nNode = nList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    String type = getTagValue("type", eElement);
                    verboseMessages.add("Type: " + type);
                    String value = getTagValue("value", eElement);
                    verboseMessages.add("Value: " + value);

                    if (type.equals("field")) {
                        String key = getTagValue("key", eElement);
                        verboseMessages.add("Key: " + key);
                        String readonly = getTagValue("readonly", eElement);
                        verboseMessages.add("Readonly: " + readonly);
                        form.setField(key, value);
                        if (readonly.equals("true")) {
                            form.setFieldProperty(key, "setfflags", PdfFormField.FF_READ_ONLY, null);
                        }
                    } else if (type.equals("image")) {

                    } else if (type.equals("text")) {
                        NodeList cList = eElement.getElementsByTagName("config");
                        Element cElement = (Element) cList.item(0);

                        String sPage = getTagValue("page", cElement);
                        int page = 1;
                        if (!sPage.equals("")) {
                            page = Integer.parseInt(sPage);
                        }
                        verboseMessages.add("Page: " + Integer.valueOf(page).toString());

                        String font = getTagValue("font", cElement);
                        verboseMessages.add("Font: " + font);
                        Integer[] stroke = getColorArray("stroke", cElement);
                        if (stroke != null) {
                            verboseMessages.add("Stroke: (" + stroke[0] + "," + stroke[1] + "," + stroke[2] + ")");
                        }

                        String sSize = getTagValue("size", cElement);
                        verboseMessages.add("Size: " + sSize);

                        String sX = getTagValue("x", cElement);
                        verboseMessages.add("X: " + sX);
                        String sY = getTagValue("y", cElement);
                        verboseMessages.add("Y: " + sY);

                        float x = Float.parseFloat(sX);
                        float y = Float.parseFloat(sY);
                        float size = Float.parseFloat(sSize);

                        PdfContentByte cb = stamper.getOverContent(page);
                        cb.saveState();
                        cb.beginText();
                        cb.moveText(x, y);
                        if (stroke != null) {
                            cb.setRGBColorFill(stroke[0], stroke[1], stroke[2]);
                        }
                        if (font.equals("")) {
                            cb.setFontAndSize(bfStandard, size);
                        } else {
                            bfCustom = BaseFont.createFont(fontPath + "/" + font, "", BaseFont.EMBEDDED);
                            cb.setFontAndSize(bfCustom, size);
                        }
                        cb.showText(value);
                        cb.endText();
                        cb.restoreState();
                    } else {
                        verboseMessages.poll();
                        verboseMessages.addFirst("Type: " + type + " is unknown");
                    }
                    if (verboseMode) {
                        while (verboseMessages.size() > 0) {
                            System.out.println(verboseMessages.poll());
                        }
                        System.out.println("-----------------------");
                    }
                }
            }

            stamper.setFormFlattening(flatten);

            stamper.close();

            System.out.println("Success");

        } catch (IOException e) {
            System.out.println("IOException");
            e.printStackTrace();
            System.exit(31);
        } catch (Exception e) {
            System.out.println("Exception");
            e.printStackTrace();
            System.exit(32);
        }

        System.out.println("Finished");

    }


    /**
     * Parse arguments
     */
    private static void setOptions(String[] args) {
        OptionParser parser = new OptionParser();
        parser.accepts("template").withRequiredArg().isRequired();
        parser.accepts("target").withRequiredArg().isRequired();
        parser.accepts("xml").withRequiredArg().isRequired();
        parser.accepts("verbose");
        parser.accepts("fonts").withRequiredArg();
        parser.accepts("flatten");

        try {
            OptionSet options = parser.parse(args);
            pdfTemplate = (String) options.valueOf("template");
            pdfTarget = (String) options.valueOf("target");
            xmlFile = (String) options.valueOf("xml");
            if (options.has("verbose")) {
                verboseMode = true;
            }
            if (options.has("fonts")) {
                fontPath = (String) options.valueOf("fonts");
            }
            if (options.has("flatten")) {
                flatten = true;
            }
        } catch (Exception e) {
            System.err.println("Missing arguments (--template, --target, --xml)");
            System.exit(1);
        }

        if (pdfTemplate == null) {
            System.err.println("Missing argument: --template (PDF template)");
            System.exit(2);
        }

        if (pdfTarget == null) {
            System.err.println("Missing argument: --target (PDF target)");
            System.exit(3);
        }

        if (xmlFile == null) {
            System.err.println("Missing argument: --xml (XML file)");
            System.exit(4);
        }

    }


    /**
     * Get nodes from XML file
     *
     * @return NodeList
     */
    private static NodeList getXmlNodes() {
        NodeList nList = null;

        try {
            File fXmlFile = new File(xmlFile);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(fXmlFile);
            doc.getDocumentElement().normalize();
            nList = doc.getElementsByTagName("field");
        } catch (Exception e) {
            System.err.println("Error parsing XML file: " + xmlFile);
            e.printStackTrace();
            System.exit(21);
        }

        return nList;

    }


    /**
     * Get tag value
     *
     * @param sTag     String
     * @param eElement Element
     * @return String
     */
    private static String getTagValue(String sTag, Element eElement) {
        NodeList nlList = eElement.getElementsByTagName(sTag);
        if (nlList.getLength() == 0) {
            return "";
        }

        NodeList nlChildList = nlList.item(0).getChildNodes();
        if (nlChildList.getLength() > 0) {
            Node nValue = nlChildList.item(0);
            return nValue.getNodeValue();
        } else {
            return "";
        }

    }

    /**
     * Get tag value
     *
     * @param sTag     String
     * @param eElement Element
     * @return Integer[]
     */
    private static Integer[] getColorArray(String sTag, Element eElement) {
        NodeList nlList = eElement.getElementsByTagName(sTag);
        if (nlList.getLength() == 0) {
            return null;
        }

        Integer[] children = new Integer[3];

        Node nValue = nlList.item(0);
        NamedNodeMap attributes = nValue.getAttributes();
        children[0] = Integer.valueOf(attributes.getNamedItem("red").getNodeValue());
        children[1] = Integer.valueOf(attributes.getNamedItem("green").getNodeValue());
        children[2] = Integer.valueOf(attributes.getNamedItem("blue").getNodeValue());
        return children;
    }


    /**
     * Print form fields
     */
    private static void printFormFields() {
        System.out.println("Form fields in template");
        System.out.println("-----------------------");

        try {

            PdfReader reader = new PdfReader(pdfTemplate);

            AcroFields form = reader.getAcroFields();
            Set<String> fields = form.getFields().keySet();

            for (String key : fields) {
                System.out.println("Name: " + key);
                System.out.print("Type: ");
                switch (form.getFieldType(key)) {
                    case AcroFields.FIELD_TYPE_CHECKBOX:
                        System.out.println("Checkbox");
                        break;
                    case AcroFields.FIELD_TYPE_COMBO:
                        System.out.println("Combobox");
                        break;
                    case AcroFields.FIELD_TYPE_LIST:
                        System.out.println("List");
                        break;
                    case AcroFields.FIELD_TYPE_NONE:
                        System.out.println("None");
                        break;
                    case AcroFields.FIELD_TYPE_PUSHBUTTON:
                        System.out.println("Pushbutton");
                        break;
                    case AcroFields.FIELD_TYPE_RADIOBUTTON:
                        System.out.println("Radiobutton");
                        break;
                    case AcroFields.FIELD_TYPE_SIGNATURE:
                        System.out.println("Signature");
                        break;
                    case AcroFields.FIELD_TYPE_TEXT:
                        System.out.println("Text");
                        break;
                    default:
                        System.out.println("?");
                }
                System.out.println("-----------------------");
            }

            if (fields.isEmpty()) {
                System.out.println("No form fields found");
                System.out.println("-----------------------");
            }

        } catch (IOException e) {
            System.out.println("Error reading form fields in PDF template (IO)");
            e.printStackTrace();
            System.exit(41);
        } catch (Exception e) {
            System.out.println("Error reading form fields in PDF template (Other)");
            e.printStackTrace();
            System.exit(42);
        }

    }


}
