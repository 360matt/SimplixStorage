package de.leonhard.storage;

import de.leonhard.storage.internal.FileData;
import de.leonhard.storage.internal.FileType;
import de.leonhard.storage.internal.FlatFile;
import de.leonhard.storage.internal.IStorage;
import de.leonhard.storage.internal.editor.yaml.YamlEditor;
import de.leonhard.storage.internal.editor.yaml.YamlParser;
import de.leonhard.storage.internal.editor.yaml.YamlReader;
import de.leonhard.storage.internal.editor.yaml.YamlWriter;
import de.leonhard.storage.internal.settings.ConfigSettings;
import de.leonhard.storage.internal.settings.DataType;
import de.leonhard.storage.internal.settings.ReloadSettings;
import de.leonhard.storage.utils.FileUtils;
import lombok.*;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Yaml extends FlatFile implements IStorage {
	protected final YamlEditor yamlEditor;
	protected final YamlParser parser;
	@Setter
	private ConfigSettings configSettings = ConfigSettings.SKIP_COMMENTS;

	public Yaml(String name, String path) {
		this(name, path, null, null, null, null);
	}

	public Yaml(String name, String path, InputStream inputStream) {
		this(name, path, inputStream, null, null, null);
	}

	public Yaml(String name,
	            String path,
	            InputStream inputStream,
	            ReloadSettings reloadSettings,
	            ConfigSettings configSettings,
	            DataType dataType) {
		super(name, path, FileType.YAML);

		if (create()) {
			if (inputStream != null) {
				FileUtils.writeToFile(file, inputStream);
			}
		}


		if (reloadSettings != null) {
			this.reloadSettings = reloadSettings;
		}

		if (configSettings != null) {
			this.configSettings = configSettings;
		}

		if (dataType != null) {
			this.dataType = dataType;
		} else {
			this.dataType = DataType.fromConfigSettings(configSettings);
		}

		yamlEditor = new YamlEditor(file);
		parser = new YamlParser(yamlEditor);
		reRead();
	}

	// ----------------------------------------------------------------------------------------------------
	// Methods to override (Points where YAML is unspecific for typical FlatFiles)
	// ----------------------------------------------------------------------------------------------------

	@Override
	public void set(String key, Object value) {
		set(key, value, this.configSettings);
	}

	@Synchronized
	public void set(String key, Object value, ConfigSettings configSettings) {
		reloadIfNeeded();

		String finalKey = (pathPrefix == null) ? key : pathPrefix + "." + key;


		fileData.insert(finalKey, value);

		try {
			//If Comments shouldn't be preserved
			if (!ConfigSettings.PRESERVE_COMMENTS.equals(configSettings)) {
				write(fileData);
				return;
			}


			final List<String> unEdited = yamlEditor.read();
			final List<String> header = yamlEditor.readHeader();
			final List<String> footer = yamlEditor.readFooter();
			write();
			header.addAll(yamlEditor.read());
			if (!header.containsAll(footer)) {
				header.addAll(footer);
			}
			write();
			yamlEditor.write(parser.parseComments(unEdited, header));
		} catch (IOException ex) {
			System.err.println("Error while writing '" + getName() + "'");
			ex.printStackTrace();
		}
	}

	// ----------------------------------------------------------------------------------------------------
	// Abstract methods to implement
	// ----------------------------------------------------------------------------------------------------

	@Override
	@SuppressWarnings("unchecked")
	protected void reRead() {
		try (final YamlReader reader = new YamlReader(new FileReader(getFile()))) {
			fileData = new FileData(reader.readToMap(), dataType);
		} catch (final IOException ex) {
			System.err.println("Error reloading Yaml '" + getName() + "'");
			System.err.println("In '" + FileUtils.getParentDirPath(file) + "'");
			ex.printStackTrace();
		}
	}

	@Override
	protected void write(FileData data) throws IOException {
		try (final YamlWriter writer = new YamlWriter(file)) {
			writer.write(data.toMap());
		}
	}

	// ----------------------------------------------------------------------------------------------------
	// Specific utility methods for YAML
	// ----------------------------------------------------------------------------------------------------

	public List<String> getHeader() {
		return yamlEditor.readHeader();
	}

	public void setHeader(final List<String> header) {
		yamlEditor.setHeader(header);
	}

	public void addHeader(final List<String> toAdd) {
		yamlEditor.addHeader(toAdd);
	}
}
