package edu.unh.cs980.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class ProjectUtils {
	// This class will contain some common utility methods for Project.

	public static void writeToFile(String filePath, ArrayList<String> runfileStrings) {
		String fullpath = filePath;
		try (FileWriter runfile = new FileWriter(new File(fullpath))) {
			for (String line : runfileStrings) {
				runfile.write(line + "\n");
			}

			runfile.close();
		} catch (IOException e) {
			System.out.println("Could not open " + fullpath);
		}
	}
}
