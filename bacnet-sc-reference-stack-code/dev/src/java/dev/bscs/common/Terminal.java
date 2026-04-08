// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.common;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

/**
 * A {@link Shell} that provides GUI window for manual commands, including command history and editing.
 * When you move the cursor up to a previous line and hit enter, that line is copied to the end and is ready to just
 * hit enter again to repeat, or you can edit it before hitting enter. For convenience, it doesn't matter where the
 * cursor is on a line when you hit enter, the entire line will be copied or accepted.
 *
 * When you are not on the last line, anything other than arrow keys, selection operations (shift arrows), and the
 * 'copy' operator will place the cursor at the end of the last line and then complete that key action. So normal typing
 * will always go on the end of the last line. And selecting text, then 'copy' and 'paste', will add the pasted text to
 * the end of the last line.
 *
 * By default, closing a Terminal window will shutdown the application. This is because closing the window actually
 * issues a "_terminal_closed_" command and the {@link Application} class processes that the same as the "quit" command.
 * This can be overridden by an individual subclass of Application by handing the "_terminal_closed_" as it desires.
 *
 * @author drobin
 */
public class Terminal extends Shell implements WindowListener   {

    private final JFrame    frame;
    private final JTextArea textArea;
    private final JPanel    statusBar;
    private final JTextArea statusText;

    private boolean         wasDeactivated = false;
    private List<String>    history = new ArrayList<>();
    private int historyPosition;


    public Terminal(String title, String prompt, int xLocation, int yLocation, int height, int width) {
        super(title,prompt);
        frame = new JFrame(title);
        textArea = new JTextArea();
        JScrollPane scrollPane = new JScrollPane();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.addWindowListener(this);
        scrollPane.setViewportView(textArea);
        textArea.addKeyListener(new KeyListener());
        textArea.setFont(new Font("Courier", Font.PLAIN, 14));
        // after deactivation and reactivation, we don't want the caret to go to the mouse click location!
        // we want it positioned at the end, ready for another command
        textArea.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (wasDeactivated) {
                    wasDeactivated = false;
                    setCaretAtEndOfText();
                }
            }
        });

        statusBar = new JPanel();
        statusBar.setLayout(new GridLayout(1,1));
        statusText = new JTextArea();
        statusBar.add(statusText);
        statusText.setFont(new Font("Courier", Font.PLAIN, 14));

        Container contentPane = frame.getContentPane();
        contentPane.add(scrollPane);
        contentPane.add(statusBar, BorderLayout.SOUTH);

        SwingUtilities.invokeLater(() -> {
            frame.setBounds(xLocation, yLocation, width, height);
            frame.setVisible(true);
            textArea.append(prompt);
            //statusText.setText(getStatusPrefix());
        });
    }

    @Override public void internalSetStatus(String s, Color c) {
        SwingUtilities.invokeLater(() -> {
            statusText.setText(getStatusPrefix()+s);
            statusText.setForeground(c);

        });
    }

    //////// generic Shell methods ///////////

    @Override protected void internalOut(String s) { textArea.append(s);setCaretAtEndOfText(); }
    @Override protected void internalCommandDone() { if (getCurrentColumn() != 0) print("\n"); print(prompt); }

    //////////////////////////////////////

    private static final SimpleDateFormat statusDateFormater = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ");
    private String getStatusPrefix() {
        return statusDateFormater.format(new Date());
    }

    private int getCurrentColumn()  {
        int  column = 0;
        String text = textArea.getText();
        int   caret = textArea.getCaretPosition()-1;
        while (caret >= 0 && text.charAt(caret--) != 10) column++;
        return column;
    }

    private String getContentsOfCurrentLine() {
        String text   = textArea.getText();
        String result = text.substring(getStartOfCurrentLine(),getEndOfCurrentLine());
        return result;
    }

    private void setCaretAtEndOfText() {
        textArea.setCaretPosition(textArea.getText().length()); // set caret at end
    }

    private void setCaretAt(int caret) {
        textArea.setCaretPosition(caret);
    }

    private void truncateAfterCaret() {
        String text = textArea.getText();
        int   caret = textArea.getCaretPosition();
        text = text.substring(0,caret);
        textArea.setText(text);
    }

    private int getStartOfCurrentLine() {
        String text = textArea.getText();
        int   start = textArea.getCaretPosition();
        while (start > 0) if (text.charAt(--start) == 10) { start++; break;}
        textArea.setCaretPosition(start);
        return start;
    }

    private int getEndOfCurrentLine() {
        String  text = textArea.getText();
        int      end = textArea.getCaretPosition();
        while (end < text.length()) if (text.charAt(end++) == 10) { end--; break; }
        return end;
    }

    private boolean isOnLastLine() {
        String  text = textArea.getText();
        int    caret = textArea.getCaretPosition();
        while (caret < text.length()) if (text.charAt(caret++) == 10) return false;
        return true;
    }

    private void clearLastLine() {
        setCaretAtEndOfText();
        setCaretAt(getStartOfCurrentLine());
        truncateAfterCaret();
    }

    private class KeyListener extends KeyAdapter {
        @Override public void keyPressed(KeyEvent evt) {
            int keyCode = evt.getKeyCode();
            if (keyCode == KeyEvent.VK_ENTER) {
                if (!commandDoneSem.get()) { // enter during command processing will cancel
                    evt.consume();
                    cancel();
                    return;
                }
                String line = getContentsOfCurrentLine();
                if (isOnLastLine()) {
                    String command = line.length()>prompt.length()? line.substring(prompt.length()):"";
                    // if this is a new line (not an immediate repeat), then add it to history
                    if (!command.isEmpty() && (history.size()==0 || !command.equals(history.get(history.size()-1)))) history.add(command);
                    historyPosition = history.size();
                    internalOut("\n");
                    internalIn(command, false); // false = non-blocking
                }
                else {
                    // User hit Enter when not on last line... first, clear anything that *is* on the last line
                    clearLastLine();
                    // then, if the current line was a command line (starts with a prompt), then copy that to the last
                    // line for possible editing or repeating.  But if it's just random text, then don't copy it. In
                    // either case, leave the cursor on the end of the last line.
                    boolean hasPrompt = line.startsWith(prompt); // TODO fix this if we allow changeable prompt because we don't know what the prompt *was* for previous lines
                    if (hasPrompt) print(line);
                    else print(prompt);
                }
                evt.consume(); // since we print the newline ourselves, we disable default
            }
            else if (isOnLastLine() && (keyCode == KeyEvent.VK_BACK_SPACE || keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_KP_LEFT)) {
                if (getCurrentColumn() <= prompt.length()) evt.consume(); // don't allow backspacing over the prompt
            }
            else if (isOnLastLine() && (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_KP_UP)) {
                if (historyPosition > 0) {
                    String line = history.get(historyPosition-1);
                    if (historyPosition>1) historyPosition--;
                    clearLastLine();
                    print(prompt);
                    print(line);
                }
                evt.consume();
            }
            else if (isOnLastLine() && (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_KP_DOWN)) {
                if (historyPosition < history.size()) {
                    String line = history.get(historyPosition);
                    if (historyPosition < history.size()-1) historyPosition++;
                    clearLastLine();
                    print(prompt);
                    print(line);
                }
                evt.consume();
            }
            else if (!isOnLastLine()) { // only allow selection and copying if not on last line
                boolean arrows =
                                keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_RIGHT ||
                                keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN ||
                                keyCode == KeyEvent.VK_KP_LEFT || keyCode == KeyEvent.VK_KP_RIGHT ||
                                keyCode == KeyEvent.VK_KP_UP || keyCode == KeyEvent.VK_KP_DOWN;
                boolean modifiers =
                                keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_CONTROL ||
                                keyCode == KeyEvent.VK_ALT || keyCode == KeyEvent.VK_ALT_GRAPH ||
                                keyCode == KeyEvent.VK_META;
                boolean modifierDown =
                        evt.isAltDown() || evt.isMetaDown() || evt.isControlDown();
                boolean copy =
                                keyCode == KeyEvent.VK_COPY ||
                                keyCode == KeyEvent.VK_C && modifierDown;
                boolean allowed =
                        arrows || modifiers || copy;
                if (!allowed) {
                    setCaretAtEndOfText(); // force everything else to the end
                    evt.consume();
                }
            }
        }
    }
    @Override public void windowClosing(WindowEvent e) { internalIn("_terminal_closed_",false); } // applications can choose to "quit" or ignore the window closure
    @Override public void windowClosed(WindowEvent e) { }
    @Override public void windowActivated(WindowEvent e)   {  }
    @Override public void windowDeactivated(WindowEvent e) { wasDeactivated = true; }
    @Override public void windowDeiconified(WindowEvent e) {  }
    @Override public void windowIconified(WindowEvent e)   {  }
    @Override public void windowOpened(WindowEvent e) { }


}
