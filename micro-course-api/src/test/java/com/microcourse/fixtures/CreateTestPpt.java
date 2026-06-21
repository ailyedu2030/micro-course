package com.microcourse;
import org.apache.poi.xslf.usermodel.*;
import java.awt.Color;
import java.awt.Rectangle;
import java.io.*;

public class CreateTestPpt {
    public static void main(String[] args) throws Exception {
        XMLSlideShow ppt = new XMLSlideShow();
        XSLFSlide s1 = ppt.createSlide();
        XSLFTextBox t1 = s1.createTextBox();
        t1.setAnchor(new Rectangle(100, 100, 600, 80));
        XSLFTextRun r1 = t1.addNewTextParagraph().addNewTextRun();
        r1.setText("微课平台 互动课程测试");
        r1.setFontSize(28.0);
        r1.setFontColor(Color.BLUE);
        
        XSLFSlide s2 = ppt.createSlide();
        XSLFTextBox t2 = s2.createTextBox();
        t2.setAnchor(new Rectangle(80, 80, 700, 120));
        XSLFTextRun r2 = t2.addNewTextParagraph().addNewTextRun();
        r2.setText("数据结构基础\n数组链表栈队列");
        r2.setFontSize(24.0);
        
        XSLFSlide s3 = ppt.createSlide();
        XSLFTextBox t3 = s3.createTextBox();
        t3.setAnchor(new Rectangle(60, 60, 700, 400));
        XSLFTextRun r3 = t3.addNewTextParagraph().addNewTextRun();
        r3.setText("总结: 掌握基本数据结构 理解复杂度 选择合适结构");
        r3.setFontSize(20.0);
        
        ppt.write(new FileOutputStream("/tmp/valid-test.pptx"));
        ppt.close();
        System.out.println("OK: created /tmp/valid-test.pptx with " + ppt.getSlides().size() + " slides");
    }
}
