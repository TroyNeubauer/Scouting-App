package com.jt.scoutcore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class ScoutingUtils {

	public static MatchSubmission read(File file) {
		try {
			Kryo kryo = ScoutingConstants.KRYO.get();
			Input input = new Input(new FileInputStream(file));
			Object obj = kryo.readClassAndObject(input);
			if (!(obj instanceof MatchSubmission)) {
				throw new RuntimeException("Invalid " + obj.getClass());
			}
			input.close();
			return (MatchSubmission) obj;
		} catch (Exception e) {

			throw new RuntimeException(e);
		}
	}

	public static void write(MatchSubmission sub, File file) {
		try {
			Output out = new Output(new FileOutputStream(file));
			Kryo kryo = ScoutingConstants.KRYO.get();
			kryo.writeClassAndObject(out, sub);
			out.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static String makeLength(String string, int length) {
		if (string.length() == length)
			return string;

		if (string.length() > length)
			return string.substring(0, length);
		char[] chars = new char[length];
		string.getChars(0, string.length(), chars, 0);
		for (int i = string.length(); i < length; i++) {
			chars[i] = ' ';
		}
		return new String(chars);
	}
}
