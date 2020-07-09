package uart.terminal.androidstudio.com.myapplication;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;

/**
 * Created by Le Trong Nhan on 31/03/2020.
 */

public class Adafruit_Thermal {

    int
        printMode,
        prevByte,      // Last character issued to printer
        column,        // Last horizontal column printed
        maxColumn,     // Page width (output 'wraps' at this point)
        charHeight,    // Height of characters, in 'dots'
        lineSpacing,   // Inter-line spacing (not line height), in dots
        barcodeHeight, // Barcode height in dots, not including text
        maxChunkHeight;
    long
        resumeTime,    // Wait until micros() exceeds this before sending byte
        dotPrintTime,  // Time to print a single dot line, in microseconds
        dotFeedTime;

    private final int PRINTER_FIRMWARE = 268;
    private final int BYTE_TIME = 2;
    private final char ASCII_TAB = '\t';
    private final char ASCII_LF = '\n';
    private final char ASCII_FF = '\f';
    private final char ASCII_CR = '\r';
    private final char ASCII_DC2 = 18;
    private final char ASCII_ESC = 27;
    private final char ASCII_FS = 28;
    private final char ASCII_GS = 29;


    // === Character commands ===

    private final byte INVERSE_MASK         =(1 << 1); // Not in 2.6.8 firmware (see inverseOn())
    private final byte UPDOWN_MASK          =(1 << 2);
    private final byte BOLD_MASK            =(1 << 3);
    private final byte DOUBLE_HEIGHT_MASK   =(1 << 4);
    private final byte DOUBLE_WIDTH_MASK    =(1 << 5);
    private final byte STRIKE_MASK          =(1 << 6);

    UsbSerialPort thermalPort;

    private void timeoutWait(){

    }
    private void timeoutSet(long duration){

    }
    private void delay(long duration){

    }
    public Adafruit_Thermal(UsbSerialPort serialPort){
        thermalPort = serialPort;
    }

    public void writeBytes(byte a) throws IOException{
        timeoutWait();
        thermalPort.write(new byte[]{a},BYTE_TIME);
    }

    public void writeBytes(byte a, byte b) throws  IOException{
        timeoutWait();
        thermalPort.write(new byte[]{a, b},2 * BYTE_TIME);
    }

    public void writeBytes(byte a, byte b, byte c) throws  IOException{
        timeoutWait();
        thermalPort.write(new byte[]{a, b, c},3 * BYTE_TIME);
    }
    public void writeBytes(byte a, byte b, byte c, byte d) throws  IOException{
        timeoutWait();
        thermalPort.write(new byte[]{a, b, c, d},4 * BYTE_TIME);
    }

    public int write(byte c) throws IOException{
        if(c != 0x13) { // Strip carriage returns
            timeoutWait();
            writeBytes(c);
            long d = BYTE_TIME;
            if((c == '\n') || (column == maxColumn)) { // If newline or wrap
                d += (prevByte == '\n') ?
                        ((charHeight+lineSpacing) * dotFeedTime) :             // Feed line
                        ((charHeight*dotPrintTime)+(lineSpacing*dotFeedTime)); // Text line
                column = 0;
                c      = '\n'; // Treat wrap as newline on next pass
            } else {
                column++;
            }
            timeoutSet(d);
            prevByte = c;
        }
        return 1;
    }
    public void wake() throws IOException {
        timeoutSet(0);   // Reset timeout counter
        writeBytes((byte)255); // Wake
        if(PRINTER_FIRMWARE >= 264) {
            delay(50);
            writeBytes((byte)(ASCII_ESC), (byte)('8'), (byte)(0), (byte)(0)); // Sleep off (important!)
        }else {
            // Datasheet recommends a 50 mS delay before issuing further commands,
            // but in practice this alone isn't sufficient (e.g. text size/style
            // commands may still be misinterpreted on wake).  A slightly longer
            // delay, interspersed with NUL chars (no-ops) seems to help.
            for (int i = 0; i < 10; i++) {
                writeBytes((byte)0);
                timeoutSet(10000L);
            }
        }
    }
    public boolean hasPaper() throws IOException{
        if(PRINTER_FIRMWARE >= 264) {
            writeBytes((byte)(ASCII_ESC), (byte)'v', (byte) 0);
        }else {
            writeBytes((byte)ASCII_GS, (byte) 'r', (byte)0);
        }

        byte[] status = new byte[100];
        thermalPort.read(status,1000);
        return !((status[0] & 4) == 0);
    }
    public void setLineHeight(int val) throws IOException{
        if(val < 24) val = 24;
        lineSpacing = val - 24;

        // The printer doesn't take into account the current text height
        // when setting line height, making this more akin to inter-line
        // spacing.  Default line spacing is 30 (char height of 24, line
        // spacing of 6).
        writeBytes((byte)ASCII_ESC, (byte)'3', (byte)val);
    }

    public void setMaxChunkHeight(int val) {
        maxChunkHeight = val;
    }

    public void setCharset(byte val) throws IOException {
        if(val > 15) val = 15;
        writeBytes((byte)ASCII_ESC, (byte)'R', val);
    }

    public void setCodePage(byte val) throws IOException {
        if(val > 47) val = 47;
        writeBytes((byte)ASCII_ESC, (byte)'t', val);
    }
    public void tab() throws IOException {
        writeBytes((byte)ASCII_TAB);
        column = (column + 4) & 0b11111100;
    }
    public void setCharSpacing(byte spacing) throws IOException {
        writeBytes((byte)ASCII_ESC, (byte)' ', spacing);
    }

    public void sleepAfter (int seconds) throws IOException {
        if (PRINTER_FIRMWARE >= 264) {
            writeBytes((byte) ASCII_ESC, (byte) '8', (byte) seconds, (byte) (seconds >> 8));
        } else {
            writeBytes((byte) ASCII_ESC, (byte) '8', (byte) seconds);
        }
    }
    public void sleep() throws IOException {
        sleepAfter(1); // Can't be 0, that means 'don't sleep'
    }
    public void offline() throws  IOException{
        writeBytes((byte)ASCII_ESC, (byte) '=', (byte)0);
    }
    public void online() throws IOException{
        writeBytes((byte)ASCII_ESC, (byte)'=', (byte)1);
    }
    public void underlineOn(byte weight) throws IOException {
        if(weight > 2) weight = 2;
        writeBytes((byte)ASCII_ESC, (byte) '-', weight);
    }
    public void underlineOff() throws IOException {
        writeBytes((byte)ASCII_ESC, (byte)'-', (byte)0);
    }
    public void setSize(byte value) throws IOException{
        byte size;

        switch(value) {
            default:  // Small: standard width and height
                size       = 0x00;
                charHeight = 24;
                maxColumn  = 32;
                break;
            case 'M': // Medium: double height
                size       = 0x01;
                charHeight = 48;
                maxColumn  = 32;
                break;
            case 'L': // Large: double width and height
                size       = 0x11;
                charHeight = 48;
                maxColumn  = 16;
                break;
        }

        writeBytes((byte)ASCII_GS, (byte)'!', size);
        prevByte = '\n'; // Setting the size adds a linefeed
    }
    public void flush() throws IOException {
        writeBytes((byte)ASCII_FF);
    }

    public void justify (byte value) throws IOException{
        byte pos = 0;

        switch(value) {
            case 'L': pos = 0; break;
            case 'C': pos = 1; break;
            case 'R': pos = 2; break;
        }

        writeBytes((byte)ASCII_ESC, (byte)'a', pos);
    }

    public void writePrintMode() throws IOException {
        writeBytes((byte)ASCII_ESC, (byte)'!', (byte)printMode);
    }

    public void normal() throws IOException {
        printMode = 0;
        writePrintMode();
    }

    public void setPrintMode (byte mask) throws IOException  {
        printMode |= mask;
        writePrintMode();
        charHeight = ((printMode & DOUBLE_HEIGHT_MASK) != 0) ? 48 : 24;
        maxColumn  = ((printMode & DOUBLE_WIDTH_MASK ) !=0 ) ? 16 : 32;
    }

    public void unsetPrintMode (byte mask) throws IOException{
        printMode &= ~mask;
        writePrintMode();
        charHeight = ((printMode & DOUBLE_HEIGHT_MASK) != 0) ? 48 : 24;
        maxColumn  = ((printMode & DOUBLE_WIDTH_MASK ) != 0) ? 16 : 32;
    }


    public void upsideDownOn() throws IOException{
        setPrintMode(UPDOWN_MASK);
    }

    public void upsideDownOff() throws IOException{
        unsetPrintMode(UPDOWN_MASK);
    }

    public void doubleHeightOn() throws IOException{
        setPrintMode(DOUBLE_HEIGHT_MASK);
    }

    public void doubleHeightOff() throws IOException{
        unsetPrintMode(DOUBLE_HEIGHT_MASK);
    }

    public void doubleWidthOn() throws IOException{
        setPrintMode(DOUBLE_WIDTH_MASK);
    }

    public void doubleWidthOff() throws IOException{
        unsetPrintMode(DOUBLE_WIDTH_MASK);
    }

    public void strikeOn() throws IOException{
        setPrintMode(STRIKE_MASK);
    }

    public void strikeOff() throws IOException{
        unsetPrintMode(STRIKE_MASK);
    }

    public void boldOn() throws IOException{
        setPrintMode(BOLD_MASK);
    }

    public void boldOff() throws IOException{
        unsetPrintMode(BOLD_MASK);
    }

    public void inverseOn() throws IOException{
        if(PRINTER_FIRMWARE >= 268)
            writeBytes((byte) ASCII_GS, (byte)'B', (byte) 1);
        else
            setPrintMode(INVERSE_MASK);
    }

    public void inverseOff() throws IOException{
        if(PRINTER_FIRMWARE >= 268)
            writeBytes((byte)ASCII_GS, (byte)'B', (byte) 0);
        else
            unsetPrintMode(INVERSE_MASK);
    }

    public void setBarcodeHeight(byte val) throws IOException { // Default is 50
        if(val < 1) val = 1;
        barcodeHeight = val;
        writeBytes((byte)ASCII_GS, (byte)'h', val);
    }

    public void reset() throws IOException {
        writeBytes((byte)ASCII_ESC, (byte)'@'); // Init command
        prevByte      = '\n';       // Treat as if prior line is blank
        column        =    0;
        maxColumn     =   32;
        charHeight    =   24;
        lineSpacing   =    6;
        barcodeHeight =   50;

        if(PRINTER_FIRMWARE >= 264) {
            // Configure tab stops on recent printers
            writeBytes((byte)ASCII_ESC, (byte)'D'); // Set tab stops...
            writeBytes((byte)4, (byte)8, (byte)12, (byte)16); // ...every 4 columns,
            writeBytes((byte)20, (byte)24, (byte)28, (byte)0); // 0 marks end-of-list.
        }
    }

    public void setDefault() throws IOException{
        online();
        justify((byte)'L');
        inverseOff();
        doubleHeightOff();
        setLineHeight(30);
        boldOff();
        underlineOff();
        setBarcodeHeight((byte)50);
        setSize((byte)'S');
        setCharset((byte)0);
        setCodePage((byte)0);
    }
}
