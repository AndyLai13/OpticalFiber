package com.lightel.opticalfiber.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.DropDownPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

import com.lightel.opticalfiber.R;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    // Fiber type
    private DropDownPreference mPrefFiberType;
    // Image
    private DropDownPreference mPrefImageFileFormat;
    private EditTextPreference mPrefFilenamePrefix;
    private EditTextPreference mPrefSequentialNumber;
    // Report
    private DropDownPreference mPrefAnalysisReportFormatStandard;
    private DropDownPreference mPrefAnalysisReportFormatSimple;
    private DropDownPreference mPrefReportFormat;
    private EditTextPreference mPrefFilePath;
    // Key
    private String mKeyFiberType;
    private String mKeyImageFileFormat;
    private String mKeyFilenamePrefix;
    private String mKeySequentialNumber;
    private String mKeyAnalysisReportFormatStandard;
    private String mKeyAnalysisReportFormatSimple;
    private String mKeyReportFormat;
    private String mKeyFilePath;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mKeyFiberType = getString(R.string.key_fiber_type);
        mKeyImageFileFormat = getString(R.string.key_image_file_format);
        mKeyFilenamePrefix = getString(R.string.key_filename_prefix);
        mKeySequentialNumber = getString(R.string.key_sequential_number);
        mKeyAnalysisReportFormatStandard = getString(R.string.key_analysis_report_format_standard);
        mKeyAnalysisReportFormatSimple = getString(R.string.key_analysis_report_format_simple);
        mKeyReportFormat = getString(R.string.key_report_format);
        mKeyFilePath = getString(R.string.key_file_path);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        mPrefFiberType = findPreference(mKeyFiberType);
        mPrefImageFileFormat = findPreference(mKeyImageFileFormat);
        mPrefFilenamePrefix = findPreference(mKeyFilenamePrefix);
        mPrefSequentialNumber = findPreference(mKeySequentialNumber);
        mPrefAnalysisReportFormatStandard = findPreference(mKeyAnalysisReportFormatStandard);
        mPrefAnalysisReportFormatSimple = findPreference(mKeyAnalysisReportFormatSimple);
        mPrefReportFormat = findPreference(mKeyReportFormat);
        mPrefFilePath = findPreference(mKeyFilePath);

        SharedPreferences spf = getPreferenceScreen().getSharedPreferences();
        String defaultValue;

        defaultValue = getResources().getStringArray(R.array.array_fiber_type)[0];
        mPrefFiberType.setSummary(spf.getString(mKeyFiberType, defaultValue));

        defaultValue = getResources().getStringArray(R.array.array_file_format)[0];
        mPrefImageFileFormat.setSummary(spf.getString(mKeyImageFileFormat, defaultValue));

        defaultValue = "con";
        mPrefFilenamePrefix.setSummary(spf.getString(mKeyFilenamePrefix, defaultValue));

        defaultValue = "0002";
        mPrefSequentialNumber.setSummary(spf.getString(mKeySequentialNumber, defaultValue));

        defaultValue = getResources().getStringArray(R.array.array_analysis_report_format_standard)[0];
        ;
        mPrefAnalysisReportFormatStandard.setSummary(spf.getString(mKeyAnalysisReportFormatStandard, defaultValue));

        defaultValue = getResources().getStringArray(R.array.array_analysis_report_format_simple)[0];
        mPrefAnalysisReportFormatSimple.setSummary(spf.getString(mKeyAnalysisReportFormatSimple, defaultValue));

        defaultValue = getResources().getStringArray(R.array.array_file_format)[0];
        mPrefReportFormat.setSummary(spf.getString(mKeyReportFormat, defaultValue));

        defaultValue = "/sdcard/0/";
        mPrefFilePath.setSummary(spf.getString(mKeyFilePath, defaultValue));
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d("Andy", "key = " + key + ", value = " + sharedPreferences.getString(key, "NA"));
        if (key.equals(mKeyFiberType)) {
            mPrefFiberType.setSummary(sharedPreferences.getString(key, "NA"));
        } else if (key.equals(mKeyImageFileFormat)) {
            mPrefImageFileFormat.setSummary(sharedPreferences.getString(key, "NA"));
        } else if (key.equals(mKeyFilenamePrefix)) {
            mPrefFilenamePrefix.setSummary(sharedPreferences.getString(key, "NA"));
        } else if (key.equals(mKeySequentialNumber)) {
            mPrefSequentialNumber.setSummary(sharedPreferences.getString(key, "NA"));
        } else if (key.equals(mKeyAnalysisReportFormatStandard)) {
            mPrefAnalysisReportFormatStandard.setSummary(sharedPreferences.getString(key, "NA"));
        } else if (key.equals(mKeyAnalysisReportFormatSimple)) {
            mPrefAnalysisReportFormatSimple.setSummary(sharedPreferences.getString(key, "NA"));
        } else if (key.equals(mKeyReportFormat)) {
            mPrefReportFormat.setSummary(sharedPreferences.getString(key, "NA"));
        } else if (key.equals(mKeyFilePath)) {
            mPrefFilePath.setSummary(sharedPreferences.getString(key, "NA"));
        }
    }
}
