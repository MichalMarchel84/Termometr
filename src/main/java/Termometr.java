import java.awt.*;
import java.awt.event.*;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.Timer;
import java.net.*;
import java.util.*;
import java.io.*;

public class Termometr {

    String ip;

    boolean connected = false;
    boolean restart = false;
    boolean dataMissing = false;
    boolean blink = false;
    boolean alarm = false;
    boolean ack = false;

    int mesNo = 0;
    int chartSize = 10;

    float tempVal = 0;
    float actualValue = 0;
    float tempOffset = 0;
    float chartMax = 80;
    float chartMin = 40;
    float alarmMax = 60;
    float alarmMin = 50;

    ArrayList<Float> chart = new ArrayList<Float>();
    JFrame main;
    JLabel tempLabel = new JLabel();
    Clip horn;


    public Termometr(String ip) {
        this.ip = ip;
        float A = 0.00012f;
        float B = -0.325f;
        float C = 190f;
        try {
            horn = AudioSystem.getClip();
            AudioInputStream in = AudioSystem.getAudioInputStream(getClass().getClassLoader().getResourceAsStream("horn.wav"));
            horn.open(in);
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        main = frame();
        while(true) {
            try {
                BufferedInputStream scan;
                Socket socket = new Socket(ip, 8000);
                connected = true;
                main.repaint();
                scan = new BufferedInputStream(socket.getInputStream());
                int volts = 0;
                Timer av = new Timer(5000, actVal);
                av.start();
                String s = "";
                int temp;
                while(socket.isConnected()) {
                    if(scan.available() > 0) {
                        temp = scan.read();
                        if(temp == 13) {
                            scan.read();
                            volts = Integer.parseInt(s);
                            tempVal += A*volts*volts+B*volts+C;
                            mesNo += 1;
                            s = "";
                        }
                        else s += (char)temp;
                    }
                    if(restart) {
                        restart = false;
                        break;
                    }
                }
                av.stop();
                scan.close();
                socket.close();
            }
            catch(Exception e) {
                main.repaint();
                connected = false;
            }
        }
    }

    ActionListener actVal = new ActionListener() {
        int i = 0;

        public void actionPerformed(ActionEvent ev) {
            if(mesNo > 0) {
                dataMissing = false;
                i = 0;
                actualValue = tempOffset + tempVal/mesNo;
                tempVal = 0;
                mesNo = 0;
                tempLabel.setText(Float.toString((float)Math.round(actualValue*10)/10));
                chart.add(actualValue);
                while(chart.size() > 720) chart.remove(0);
                if((actualValue > alarmMax) || (actualValue < alarmMin)) {
                    if(!horn.isActive() && !alarm) horn.loop(5);
                    alarm = true;
                }
                else if(ack && connected) {
                    alarm = false;
                    ack = false;
                }
            }
            else {
                i++;
                dataMissing = true;
            }
            if(i > 5) {
                i = 0;
                restart = true;
                dataMissing = false;
                connected = false;
                if(!horn.isActive() && !alarm) horn.loop(5);
                alarm = true;
            }
            main.repaint();
        }
    };

    JFrame frame() {
        JFrame f = new JFrame("Termometr");
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());
        p.add(sidePanel(), BorderLayout.EAST);
        p.add(chartPanel(), BorderLayout.CENTER);
        f.add(p);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        return f;
    }

    JPanel chartPanel() {
        int meshVer = 10;
        int meshHor = 5;
        int frameSize = 15;
        int scaleSize = 40;
        JPanel p = new JPanel() {
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D)g;
                g2d.setColor(Color.BLACK);
                g2d.fillRect(frameSize, frameSize, this.getWidth()-scaleSize-frameSize, this.getHeight()-scaleSize-frameSize);
                for(int i = 0; i < (meshHor + 1); i++) {
                    float x = chartMin + i*(float)(chartMax - chartMin)/meshHor;
                    g2d.setColor(Color.BLACK);
                    g2d.drawString(Float.toString((float)Math.round(x*10)/10), this.getWidth()-scaleSize+5, this.getHeight()-scaleSize-i*(this.getHeight()-frameSize-scaleSize)/meshHor);
                    g2d.setColor(Color.GRAY);
                    g2d.drawLine(frameSize, this.getHeight()-scaleSize-i*(this.getHeight()-frameSize-scaleSize)/meshHor, this.getWidth()-scaleSize, this.getHeight()-scaleSize-i*(this.getHeight()-frameSize-scaleSize)/meshHor);
                }
                for(int i = 0; i < (meshVer + 1); i++) {
                    float x = i*(float)chartSize/meshVer;
                    g2d.setColor(Color.BLACK);
                    g2d.drawString(Float.toString((float)Math.round(x*10)/10), this.getWidth()-scaleSize-i*(this.getWidth()-scaleSize-frameSize)/meshVer, this.getHeight()+15-scaleSize);
                    g2d.setColor(Color.GRAY);
                    g2d.drawLine(this.getWidth()-scaleSize-i*(this.getWidth()-frameSize-scaleSize)/meshVer, frameSize, this.getWidth()-scaleSize - i*(this.getWidth()-frameSize-scaleSize)/meshVer, this.getHeight()-scaleSize);
                }
                g2d.setColor(Color.RED);
                float fy = (this.getHeight()-frameSize-scaleSize)/(chartMax - chartMin);
                g2d.drawLine(frameSize, this.getHeight()-scaleSize-(int)(fy*(alarmMax-chartMin)), this.getWidth()-scaleSize, this.getHeight()-scaleSize-(int)(fy*(alarmMax-chartMin)));
                g2d.setColor(Color.BLUE);
                g2d.drawLine(frameSize, this.getHeight()-scaleSize-(int)(fy*(alarmMin-chartMin)), this.getWidth()-scaleSize, this.getHeight()-scaleSize-(int)(fy*(alarmMin-chartMin)));
                g2d.setColor(Color.GREEN);
                float fx = (float)(this.getWidth()-frameSize-scaleSize)*5/(chartSize*60);
                if(chart.size() > 0) {
                    float y0 = (float)chart.get(chart.size()-1);
                    if(y0 < chartMin) y0 = chartMin;
                    if(y0 > chartMax) y0 = chartMax;
                    for(int i = 1; i < chart.size(); i++) {
                        if(i > (chartSize*60/5 - 1)) break;
                        float y1 = (float)chart.get(chart.size() - i - 1);
                        if(y1 < chartMin) y1 = chartMin;
                        if(y1 > chartMax) y1 = chartMax;
                        g2d.drawLine(this.getWidth()-scaleSize - (int)((i-1)*fx), this.getHeight()-scaleSize - (int)(fy*(y0 - chartMin)), this.getWidth()-scaleSize - (int)(i*fx), this.getHeight()-scaleSize - (int)(fy*(y1 - chartMin)));
                        y0 = y1;
                    }
                }
            }
        };
        p.setLayout(null);
        p.setPreferredSize(new Dimension(800, 600));
        return p;
    }

    JPanel sidePanel() {
        int width = 300;
        int height = 600;
        float statusSize = 0.1f;
        float tempSize = 0.3f;
        float settingsSize = 0.4f;
        float ackSize = 0.2f;

        JPanel p = new JPanel();
        p.setLayout(null);
        p.setPreferredSize(new Dimension(width, height));
        ActionListener bt = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if(dataMissing || (alarm && !ack)) {
                    blink = !blink;
                    main.repaint();
                }
            }
        };
        Timer b = new Timer(500, bt);
        b.start();
        FocusListener sl = new FocusListener() {
            public void focusGained(FocusEvent fe) {

            }
            public void focusLost(FocusEvent fe) {
                JTextField field = (JTextField)fe.getSource();
                switch(field.getName()) {
                    case "tmin":
                        try {
                            chartMin = Float.parseFloat(field.getText());
                        }
                        catch(Exception ex) {
                            field.setText(Float.toString(chartMin));
                        }
                        break;
                    case "tmax":
                        try {
                            chartMax = Float.parseFloat(field.getText());
                        }
                        catch(Exception ex) {
                            field.setText(Float.toString(chartMax));
                        }
                        break;
                    case "amin":
                        try {
                            alarmMin = Float.parseFloat(field.getText());
                        }
                        catch(Exception ex) {
                            field.setText(Float.toString(alarmMin));
                        }
                        break;
                    case "amax":
                        try {
                            alarmMax = Float.parseFloat(field.getText());
                        }
                        catch(Exception ex) {
                            field.setText(Float.toString(alarmMax));
                        }
                        break;
                    case "ct":
                        try {
                            chartSize = Integer.parseInt(field.getText());
                        }
                        catch(Exception ex) {
                            field.setText(Integer.toString(chartSize));
                        }
                        break;
                    case "cr":
                        try {
                            tempOffset = Float.parseFloat(field.getText());
                        }
                        catch(Exception ex) {
                            field.setText(Float.toString(tempOffset));
                        }
                        break;
                }
                main.repaint();
            }
        };
        ActionListener ab = new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                ack = true;
                horn.stop();
                horn.setMicrosecondPosition(0);
            }
        };
        JPanel status = new JPanel() {
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D)g;
                g2d.setColor(Color.BLACK);
                g2d.drawString("Status", this.getWidth()/2 - g2d.getFontMetrics(this.getFont()).stringWidth("Status")/2, 15);
                if(connected) {
                    if(!dataMissing) g2d.setColor(Color.GREEN);
                    else if(blink) g2d.setColor(Color.GREEN);
                    else g2d.setColor(new Color(0, 50, 0));
                    g2d.fillOval(this.getWidth()/2 - 50, 20, 30, 30);
                    g2d.setColor(new Color(70, 0, 0));
                    g2d.fillOval(this.getWidth()/2 + 20, 20, 30, 30);
                }
                else {
                    g2d.setColor(new Color(0, 50, 0));
                    g2d.fillOval(this.getWidth()/2 - 50, 20, 30, 30);
                    g2d.setColor(Color.RED);
                    g2d.fillOval(this.getWidth()/2 + 20, 20, 30, 30);
                }
            }
        };
        status.setSize(width, (int)(height*statusSize));
        p.add(status);
        status.setLocation(0, 0);
        tempLabel.setFont(new Font(tempLabel.getFont().getFontName(), Font.BOLD, 80));
        tempLabel.setPreferredSize(new Dimension(200, 200));
        tempLabel.setHorizontalAlignment(JLabel.CENTER);
        tempLabel.setVerticalAlignment(JLabel.CENTER);
        tempLabel.setSize(width, (int)(height*tempSize));
        p.add(tempLabel);
        tempLabel.setLocation(0, (int)(height*statusSize));
        JPanel settings = new JPanel();
        settings.setLayout(null);
        settings.setSize(width, (int)(height*settingsSize));
        JLabel min = new JLabel("min");
        min.setSize((int)(0.9*width/3), (int)(0.9*height*settingsSize)/5);
        min.setHorizontalAlignment(JLabel.CENTER);
        JLabel max = new JLabel("max");
        max.setSize((int)(0.9*width/3), (int)(0.9*height*settingsSize)/5);
        max.setHorizontalAlignment(JLabel.CENTER);
        JLabel temp = new JLabel("Temp.");
        temp.setSize((int)(0.9*width/3), (int)(0.9*height*settingsSize)/5);
        temp.setHorizontalAlignment(JLabel.CENTER);
        JTextField tmin = new JTextField(Float.toString(chartMin));
        tmin.setSize((int)(0.9*width/3), (int)(0.9*height*settingsSize)/5);
        tmin.setFont(new Font(tmin.getFont().getFontName(), Font.BOLD, 30));
        tmin.setHorizontalAlignment(JTextField.CENTER);
        tmin.setName("tmin");
        tmin.addFocusListener(sl);
        JTextField tmax = new JTextField(Float.toString(chartMax));
        tmax.setSize((int)(0.9*width/3), (int)(0.9*height*settingsSize)/5);
        tmax.setFont(new Font(tmin.getFont().getFontName(), Font.BOLD, 30));
        tmax.setHorizontalAlignment(JTextField.CENTER);
        tmax.setName("tmax");
        tmax.addFocusListener(sl);
        JLabel al = new JLabel("Alarm");
        al.setSize((int)(0.9*width/3), (int)(0.9*height*settingsSize)/5);
        al.setHorizontalAlignment(JLabel.CENTER);
        JTextField amin = new JTextField(Float.toString(alarmMin));
        amin.setSize((int)(0.9*width/3), (int)(0.9*height*settingsSize)/5);
        amin.setFont(new Font(tmin.getFont().getFontName(), Font.BOLD, 30));
        amin.setHorizontalAlignment(JTextField.CENTER);
        amin.setName("amin");
        amin.addFocusListener(sl);
        JTextField amax = new JTextField(Float.toString(alarmMax));
        amax.setSize((int)(0.9*width/3), (int)(0.9*height*settingsSize)/5);
        amax.setFont(new Font(tmin.getFont().getFontName(), Font.BOLD, 30));
        amax.setHorizontalAlignment(JTextField.CENTER);
        amax.setName("amax");
        amax.addFocusListener(sl);
        settings.add(min);
        settings.add(max);
        settings.add(temp);
        settings.add(tmin);
        settings.add(tmax);
        settings.add(al);
        settings.add(amin);
        settings.add(amax);
        min.setLocation(width/3, 15);
        max.setLocation(2*width/3, 15);
        temp.setLocation(0, (int)(height*settingsSize)/5);
        tmin.setLocation(width/3, (int)(height*settingsSize)/5);
        tmax.setLocation(2*width/3, (int)(height*settingsSize)/5);
        al.setLocation(0, 2*(int)(height*settingsSize)/5);
        amin.setLocation(width/3, 2*(int)(height*settingsSize)/5);
        amax.setLocation(2*width/3, 2*(int)(height*settingsSize)/5);
        JLabel czas = new JLabel("<html><center>Czas (1-60 min.)</center></html>");
        czas.setSize((int)(0.9*width/3), (int)(0.9*height*settingsSize)/5);
        czas.setHorizontalAlignment(JLabel.CENTER);
        JTextField ct = new JTextField(Integer.toString(chartSize));
        ct.setSize((int)(0.9*width/3), (int)(0.9*height*settingsSize)/5);
        ct.setFont(new Font(tmin.getFont().getFontName(), Font.BOLD, 30));
        ct.setHorizontalAlignment(JTextField.CENTER);
        ct.setName("ct");
        ct.addFocusListener(sl);
        JLabel corr = new JLabel("Poprawka");
        corr.setSize((int)(0.9*width/3), (int)(0.9*height*settingsSize)/5);
        corr.setHorizontalAlignment(JLabel.CENTER);
        JTextField cr = new JTextField(Float.toString(tempOffset));
        cr.setSize((int)(0.9*width/3), (int)(0.9*height*settingsSize)/5);
        cr.setFont(new Font(tmin.getFont().getFontName(), Font.BOLD, 30));
        cr.setHorizontalAlignment(JTextField.CENTER);
        cr.setName("cr");
        cr.addFocusListener(sl);
        settings.add(czas);
        settings.add(ct);
        settings.add(corr);
        settings.add(cr);
        czas.setLocation(0, 3*(int)(height*settingsSize)/5);
        ct.setLocation(width/3, 3*(int)(height*settingsSize)/5);
        corr.setLocation(0, 4*(int)(height*settingsSize)/5);
        cr.setLocation(width/3, 4*(int)(height*settingsSize)/5);
        p.add(settings);
        settings.setLocation(0, (int)(height*(statusSize + tempSize)));
        JButton a = new JButton("<html><center>Potwierdzenie alarmu</center></html>") {
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                if(alarm && !ack) {
                    if(blink) this.setBackground(Color.RED);
                    else this.setBackground(new JButton().getBackground());
                }
                else if(alarm) this.setBackground(Color.RED);
                else this.setBackground(new JButton().getBackground());
            }
        };
        a.setFont(new Font(a.getFont().getFontName(), Font.BOLD, 25));
        a.setSize((int)(0.8*width), (int)(height*ackSize*0.8));
        a.addActionListener(ab);
        p.add(a);
        a.setLocation((int)(width*0.1), height - (int)(height*ackSize));
        return p;
    }

    public static void main(String[] args) {
        new Termometr(args[0]);
    }

}
