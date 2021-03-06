package com.jt.scoutserver;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.apache.commons.io.FileUtils;

import com.jt.scoutcore.MatchSubmission;
import com.jt.scoutcore.ScoutingConstants;
import com.jt.scoutcore.ScoutingUtils;
import com.jt.scoutserver.utils.AndroidUtils;
import com.jt.scoutserver.utils.SystemUtils;
import com.jt.scoutserver.utils.Utils;

import se.vidstige.jadb.JadbConnection;
import se.vidstige.jadb.JadbDevice;
import se.vidstige.jadb.JadbException;
import se.vidstige.jadb.RemoteFile;

public class Server extends JFrame {

	public static final String PHONE_SAVE_DIR = ScoutingUtils.getSaveDir(), PHONE_MATCHES_DIR = PHONE_SAVE_DIR + ScoutingConstants.ANDROID_MATCHES_SAVE_DIRECTORY_NAME;

	public static final File APPDATA_STORAGE_FOLDER = new File(System.getenv("APPDATA"), ScoutingConstants.FOLDER_NAME);
	public static final File COMPUTER_MATCHES_DIR = new File(APPDATA_STORAGE_FOLDER, "Matches");
	private JTextArea console = new JTextArea(10, 30);
	private DefaultListModel<MatchSubmission> model = new DefaultListModel<MatchSubmission>();
	private JList<MatchSubmission> list = new JList<MatchSubmission>(model);
	private JButton pull = new JButton("Pull"), export = new JButton("Export to Excel"), purgeOldMatches = new JButton("Purge Old Matches");

	public Server() {
		super("Scouting App Server");

		PrintStream origionalOut = System.out;
		System.setOut(new PrintStream(new OutputStream() {

			@Override
			public void write(int b) throws IOException {
				String currentText = console.getText();
				char[] newArray = new char[currentText.length() + 1];
				currentText.getChars(0, currentText.length(), newArray, 0);
				newArray[currentText.length()] = (char) b;
				console.setText(new String(newArray));
				origionalOut.write(b);
			}
		}));

		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setSize(800, 600);
		setLocationRelativeTo(null);

		JPanel panel = new JPanel(new BorderLayout());
		console.setEditable(false);
		JScrollPane scroll = new JScrollPane(console);
		scroll.setBorder(BorderFactory.createTitledBorder("Console"));
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		pull.addActionListener((e) -> {
			pull();
		});
		export.addActionListener((e) -> {
			ExportWindow window = new ExportWindow();

		});
		purgeOldMatches.addActionListener((e) -> {
			int result = JOptionPane.showConfirmDialog(this, "Do you want to delete all matches stored on your computer?\nYou cannot undo this", "Delete all matches?", JOptionPane.YES_NO_OPTION);
			if (result == 0) {
				deleteMatches();
			}
		});

		panel.add(scroll, BorderLayout.SOUTH);
		panel.add(new JLabel("Indexed Matches"), BorderLayout.WEST);

		scroll = new JScrollPane(list);
		scroll.setBorder(BorderFactory.createTitledBorder("Pulled Files"));
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		panel.add(scroll, BorderLayout.WEST);

		JPanel north = new JPanel(new FlowLayout(FlowLayout.CENTER));
		north.add(pull);
		north.add(export);
		north.add(purgeOldMatches);

		panel.add(north, BorderLayout.NORTH);

		setContentPane(panel);
		setVisible(true);

		refreshFiles();

		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				setVisible(false);
				SystemUtils.nativeExit();
				System.exit(0);
			}
		});
	}

	private void deleteMatches() {
		model.clear();
		for (File file : COMPUTER_MATCHES_DIR.listFiles()) {
			try {
				FileUtils.forceDelete(file);
			} catch (IOException e) {
				e.printStackTrace(System.out);
				try {
					FileUtils.forceDelete(file);
				} catch (IOException e2) {
					e2.printStackTrace(System.out);
					try {
						FileUtils.forceDelete(file);
					} catch (IOException e3) {
						e3.printStackTrace(System.out);
					}
				}
			}
		}
	}

	private void refreshFiles() {
		File[] files = COMPUTER_MATCHES_DIR.listFiles();
		if (files == null)
			COMPUTER_MATCHES_DIR.mkdirs();
		else {
			for (File file : files) {
				if (file.isDirectory())
					continue;
				try {
					MatchSubmission submission = ScoutingUtils.read(file);
					if (!model.contains(submission)) {
						model.addElement(submission);
						list.repaint();
						Thread.sleep(1);
					}
				} catch (Exception e) {
					// ignore
					System.out.println("invalid file " + file);
				}
			}
		}
	}

	public void pull() {
		COMPUTER_MATCHES_DIR.mkdirs();
		System.out.println("\nPreparing to pull fines...");
		boolean pulled = false;
		try {
			JadbConnection jadb = new JadbConnection();
			List<JadbDevice> devices = jadb.getDevices();
			System.out.println("Detected " + devices.size() + (devices.size() == 1 ? " Device" : " Devices"));
			System.out.println("About to read " + PHONE_MATCHES_DIR);
			for (JadbDevice device : devices) {
				device.execute("mkdirs", PHONE_MATCHES_DIR);
				List<RemoteFile> files = device.list(PHONE_MATCHES_DIR);
				if (files.size() == 0)
					System.out.println("No new files to pull from " + device.toString());
				for (RemoteFile remoteFile : files) {
					if (remoteFile.isDirectory())
						continue;// This could be a symptom of another problem...
					File local = new File(COMPUTER_MATCHES_DIR, remoteFile.getPath());
					RemoteFile fileToPull = new RemoteFile(PHONE_MATCHES_DIR + "/" + remoteFile.getPath());
					System.out.println("has file " + AndroidUtils.hasFile(device, fileToPull) + ", " + fileToPull.getPath());
					if (local.exists()) {
						System.out.println("Skipping already existing file " + remoteFile.getPath() + " on " + device.toString());
						continue;
					}
					device.pull(fileToPull, new File(COMPUTER_MATCHES_DIR, remoteFile.getPath()));
					System.out.println("Pulled file " + remoteFile.getPath() + " from " + device.toString());
					pulled = true;
				}
			}

		} catch (IOException | JadbException e) {
			Utils.showNotification("Unable to access an android device", "Failed to create an adb connection...");
			System.out.println("Failed to connect to adb");
			e.printStackTrace(System.out);
		}
		if (pulled) {
			refreshFiles();
		}

	}

}
