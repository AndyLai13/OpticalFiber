package com.lightel.opticalfiber;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

public class SaveImageReportActivity extends AppCompatActivity {


    private Spinner mSpinnerImageFileFormat;
    private Spinner mSpinnerAnalysisReportFormatStandard;
    private Spinner mSpinnerAnalysisReportFormatSimple;
    private Spinner mSpinnerReportFormat;

    String[] imageFileFormat = {"BMP", "JPG", "PNG", "GIF"};
    String[] analysisReportFormatStandard = {"Standard", "Extended"};
    String[] analysisReportFormatSimple = {"Simple", "Detailed"};
    String[] reportFormat = {"Text", "Excel"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_save_image_report);

        mSpinnerImageFileFormat = findViewById(R.id.spinnerImageFileFormat);
        mSpinnerAnalysisReportFormatStandard = findViewById(R.id.spinnerStandard);
        mSpinnerAnalysisReportFormatSimple = findViewById(R.id.spinnerSimple);
        mSpinnerReportFormat = findViewById(R.id.spinnerReportFormat);


        mSpinnerImageFileFormat.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, imageFileFormat));
        mSpinnerAnalysisReportFormatStandard.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, analysisReportFormatStandard));
        mSpinnerAnalysisReportFormatSimple.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, analysisReportFormatSimple));
        mSpinnerReportFormat.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, reportFormat));
    }
}
