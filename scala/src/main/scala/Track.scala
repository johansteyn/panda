package panda

import java.io.File
import java.io.IOException
import java.util.logging.Level

import javax.sound.sampled._

import Util._
import panda.equalizer._

// TODO:
// - Make it an Akka Actor
//   The play method in Java is synchronized to ensure that only one track cna be played at a time
//   In Scala there is no synchronized keyword, but Akka Actor might be a better solution...
// - Instead of null, look at using Option, Some and None
//     http://danielwestheide.com/blog/2012/12/19/the-neophytes-guide-to-scala-part-5-the-option-type.html
//     https://alvinalexander.com/scala/initialize-scala-variables-option-none-some-null-idiom
//     https://alvinalexander.com/scala/scala-null-values-option-uninitialized-variables
//     https://stackoverflow.com/questions/2440134/is-this-the-proper-way-to-initialize-null-references-in-scal
//   Tried it for gainControl, but I don't see the point...
//   Instead of having to check for null (to avoid NullPointerException)
//   we now need to check for None, or get java.util.NoSuchElementException: None.get
class Track (val filename: String) /* implements Comparable */ {
	println("Construction Track for: " + filename)
	private var _title = filename
	// TODO: tags...
	// TODO: checked...
	// TODO: missing...
	private var _position = 0
	private var newPosition = -1
//	private var gainControl: FloatControl = null
	private var gainControl = None: Option[FloatControl]
	private var balanceControl: FloatControl = null
	private var equalizerControl: IIRControls = null
	private var _volume = 14
	private var _balance = 0
	private var _fade = false
	// Functions to "listen" for changes
	private var started: Track => Unit = null
	private var positionChanged: Track => Unit = null

	log(Level.FINE, "Getting audio input stream for file: " + filename)
	val file = new File(Panda.TRACKS + filename)
	val ais = AudioSystem.getAudioInputStream(file)
	val audioFormat = ais.getFormat()
	log(Level.FINE, "Audio format: " + audioFormat)
	log(Level.FINE, "  Channels=" + audioFormat.getChannels())
	log(Level.FINE, "  Encoding=" + audioFormat.getEncoding())
	log(Level.FINE, "  FrameRate=" + audioFormat.getFrameRate())
	log(Level.FINE, "  FrameSize=" + audioFormat.getFrameSize())
	log(Level.FINE, "  SampleRate=" + audioFormat.getSampleRate())
	log(Level.FINE, "  SampleSizeInBits=" + audioFormat.getSampleSizeInBits())
	log(Level.FINE, "  isBigEndian? " + audioFormat.isBigEndian())
	private val channels = audioFormat.getChannels()
	log(Level.FINE, "Calculating duration...")
	val available = ais.available()
	private val _duration = available / (audioFormat.getFrameRate() * audioFormat.getFrameSize())
	log(Level.FINE, "Duration: " + _duration + " seconds")
	// Note: Must close stream - cannot keep all the files open at the same time (IOException: Too many open files)
	ais.close()

	// Duration getter
	def duration = _duration

	// Position getter
	def position = _position

	// Position setter
	def position_=(value: Int) {
		if (value > duration) {
			Player.stop()
			return
		}
		if (value < 0) {
			position = 0
		}
		newPosition = value
	}

	// Volume getter
	def volume = _volume

	// Volume setter
	// Derives a gain value from the volume level as follows:
	//   volume  0 = minimum gain
	//   volume 14 = zero gain
	//   volume 20 = maximum gain
	// Values in-between are calculated to dB values
	def volume_=(value: Int) {
		_volume = value
//		if (gainControl == null) {
		if (gainControl == None) {
			return
		}
		var gain = 0.0f
//		val min = gainControl.getMinimum()
//		val max = gainControl.getMaximum()
		val gc: FloatControl = gainControl.get
		val min = gc.getMinimum()
		val max = gc.getMaximum()
		if (value > 14) {
			gain = max - (20 - value) * max / 6
		} else if (value < 14) {
			gain = (14 - value) * min / 14
		}
		log(Level.FINE, "Setting gain to:" + gain)
//		gainControl.setValue(gain)
		gc.setValue(gain)
	}

	// Balance getter
	def balance = _balance

	// Balance setter
	// Value can range from -10 to 10	
	def balance_=(value: Int) {
		_balance = value
		if (balanceControl == null) {
			return
		}
		var floatValue = 0.0f
		// Assuming balance ranges from -1.0 to 1.0
		if (value != 0) {
			floatValue = value / 10.0f
		}
		Util.log(Level.FINE, "Setting balance to: " + _balance + "(float value " + floatValue + ")")
		balanceControl.setValue(floatValue)
	}

	def setEqualizer(band: Int, value: Int) {
		if (equalizerControl == null) {
			return;
		}
		val floatValue = (value.asInstanceOf[Float]) / 10.0f / 5.0f  // Value can range between -0.2 and +0.2
		Util.log(Level.FINE, "Setting equalizer band #" + band + " to: " + value + "(float=" + floatValue + ")")
		for (i <- 0 until channels) {
			equalizerControl.setBandValue(band, i, floatValue)
		}
	}

	def setEqualizer() {
		for (i <- 0 until Config.bands) {
			setEqualizer(i, if (Player.equalizerEnabled) Player.getEqualizer(i) else 0);
		}
	}

	// Fade getter
	def fade = _fade

	// Fade setter
	def fade_=(value: Boolean) {
		_fade = value
	}

	// Can be called any number of times.
	// Each time it is called, the track starts playing from the start
	// Should only be invoked once at a time (across all instances)
	// ie. must not be called while already playing!
	def play() {
		Player.track = this
		log("------------ Playing " + filename + " ------------")
		val file = new File(Panda.TRACKS + filename)
		// Note, when a track is played, it is by definition NOT stopped and at position 0.
		// However, it may be paused, in which case it will get everything ready to play and then wait until it is unpaused before continuing
		Player.play()
		position = 0
		newPosition = -1
		// Make sure we obtain a fresh input stream...
		var ais = AudioSystem.getAudioInputStream(file)
		var eais = new EqualizerAudioInputStream(ais, Config.bands)

		log(Level.FINE, "Obtaining line...")
		val info: DataLine.Info = new DataLine.Info(classOf[SourceDataLine], audioFormat)
		val line: SourceDataLine = AudioSystem.getLine(info).asInstanceOf[SourceDataLine]

		log(Level.FINE, "Adding line listener...")
		line.addLineListener(new WavLineListener())

		log(Level.FINE, "Opening line...")
		line.open(audioFormat)
		// Cannot obtain controls before line is opened, yet need to set them before playing any sound
		if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
//			gainControl = line.getControl(FloatControl.Type.MASTER_GAIN).asInstanceOf[FloatControl]
			gainControl = Some(line.getControl(FloatControl.Type.MASTER_GAIN).asInstanceOf[FloatControl])
		} else {
//			gainControl = null
			gainControl = None
		}
		if (line.isControlSupported(FloatControl.Type.BALANCE)) {
			balanceControl = line.getControl(FloatControl.Type.BALANCE).asInstanceOf[FloatControl]
		} else {
			balanceControl = null
		}
		equalizerControl = eais.getControls()
		// Make sure new instance has volume, balance and equalizer set to same level as previous instance
		volume = Player.volume
		balance = Player.balance
		setEqualizer()
		fade = false
		log(Level.FINE, "Starting line...")
		line.start()

		// Invoke only after controls have been obtained...
		if (started != null) {
			started(this)
		}

		val buffer = new Array[Byte](Track.BUFFER_SIZE)
		var totalRead = 0
		var timestamp = System.currentTimeMillis()
		var read = 1
		while (read > 0) {
			log(Level.FINE, "Available: " + eais.available())
			read = eais.read(buffer, 0, buffer.length)
			if (read > 0 && !Player.stopped) {
				totalRead += read
				val i = totalRead / (audioFormat.getFrameRate() * audioFormat.getFrameSize()).asInstanceOf[Int]
				if (_position != i) {
					_position = i
					log(Level.FINE, "Position: " + position + " seconds")
					if (positionChanged != null) {
						positionChanged(this)
					}
				}
				while (Player.paused && !Player.stopped) {
					pause(100)
				}
				var skipped = 0L
				if (!Player.stopped) {
					if (newPosition >= 0) {
						val seek = (newPosition * audioFormat.getFrameRate() * audioFormat.getFrameSize()).asInstanceOf[Long]
						if (newPosition > position) {
							log(Level.FINE, "Seeking forward to position: " + newPosition)
							skipped = eais.skip(seek - totalRead)
							totalRead += skipped.asInstanceOf[Int]
						} else if (newPosition < position) {
							log(Level.FINE, "Seeking backward to position: " + newPosition)
							// Need to obtain new input stream in order to be able to seek backwards
							ais = AudioSystem.getAudioInputStream(file)
							eais = new EqualizerAudioInputStream(ais, Config.bands)
							equalizerControl = eais.getControls()
							setEqualizer()
							skipped = eais.skip(seek)
							totalRead = skipped.asInstanceOf[Int]
						}
						newPosition = -1
					}
					if (fade && System.currentTimeMillis() - timestamp > 1000) {
						// Reduce volume if more than a second has passed
						val newVolume = volume - 1
						if (newVolume < 0) {
							read = 0 // Since there is no break statement...
						} else {
							volume = newVolume
							timestamp = System.currentTimeMillis()
						}
					}
					if (skipped == 0) {
						line.write(buffer, 0, read)
					}
				}
			}
		}
		log(Level.FINE, "Closing line...")
		line.drain()
		line.close()
		eais.close()
	}

	override def toString(): String = {
		val sb = new StringBuffer()
		sb.append("filename=")
		sb.append(filename)
		sb.append(", title=")
		sb.append(title)
//		if (tags.size() > 0) {
//			sb.append(", tags: ")
//			Set set = tags.keySet()
//			Iterator it = set.iterator()
//			while (it.hasNext()) {
//				String key = (String) it.next()
//				String value = tags.get(key)
//				sb.append(key)
//				sb.append("=")
//				sb.append(value)
//				if (it.hasNext()) {
//					sb.append(",")
//				}
//			}
//		}
		return sb.toString()
	}

	// Title getter
	def title = _title

	// Title setter
	def title_=(value: String) {
		_title = value
	}

}

object Track extends App {
	val BUFFER_SIZE = 8192 // 8KB

	println("Player")
	for (arg <- args) {
		println("Arg: " + arg)
		val track = new Track(arg)
//		track.started = foo;
		track.started = (x) => {
			log("******* STARTED *******")
		}
		track.positionChanged = (x) => {
			log("******* POSITION CHANGED: " + track.position + " *******")
		}
		track.play()
	}
	println("Done.")
}

object Player {
	var track: Track = null
	private var _stopped = false
	private var _paused = false
	private var _volume = 14
	private var _balance = 0
	private var _equalizerEnabled = false
	private val _equalizer = new Array[Int](Config.bands)

	// Stopped getter
	def stopped = _stopped

	// Stopped setter
	def stopped_=(value: Boolean) {
		_stopped = value
	}

	def stop() {
		_stopped = true;
	}

	// Synonym for: stopped(false)
	def play() {
		_stopped = false;
	}

	// Paused getter
	def paused = _paused

	// Paused setter
	def paused_=(value: Boolean) {
		_paused = value
	}

	// Volume getter
	def volume = _volume

	// Volume setter
	// Limits range from -20 to 20
	def volume_=(value: Int) {
		_volume = value
		if (value >= 20) {
			_volume = 20
		} else if (value <= 0) {
			_volume = 0
		}
		log(Level.FINE, "Setting volume to: " + _volume)
		if (track != null) {
			track.volume = _volume
		}
	}

	// Balance getter
	def balance = _balance

	// Balance setter
	// Limits range from -10 to 10
	def balance_=(value: Int) {
		_balance = value
		if (value >= 10) {
			_volume = 10
		} else if (value <= -10) {
			_volume = -10
		}
		log(Level.FINE, "Setting balance to: " + _balance)
		if (track != null) {
			track.balance = _volume
		}
	}

	// EqualizerEnabled getter
	def equalizerEnabled = _equalizerEnabled

	// EqualizerEnabled setter
	// Also sets the equalizer control to either zero or the stored value
	def equalizerEnabled_=(value: Boolean) {
		_equalizerEnabled = value
		if (track != null) {
			for (i <- 0 until equalizer.size) {
				if (_equalizerEnabled) {
					track.setEqualizer(i, _equalizer(i))
				} else {
					track.setEqualizer(i, 0)
				}
			}
		}
	}

	// Equalizer getter
	def equalizer = _equalizer

	def getEqualizer(band: Int): Int = {
		return equalizer(band);
	}

	// Value can range from -10 to 10	
	def setEqualizer(band: Int, value: Int) {
		_equalizer(band) = value
		if (track != null && _equalizerEnabled) {
			track.setEqualizer(band, value)
		}
	}
}

object Config {
	val bands = 10
}

// TODO: Move Panda class to a separate file...
object Panda {
	val TRACKS = "tracks/"
}

object Util {
	def log(level: Level, message: String) {
		println(message)
	}

	def log(message: String) {
		log(Level.INFO, message)
	}

	def pause(millis: Long) {
		Thread.sleep(millis)
	}

}

class WavLineListener extends LineListener {
	var first = true

	def update(event: LineEvent) {
		Util.log(Level.FINE, "Line event:" + event)
		if (first) {
			first = false
			val line = event.getLine()
			printInfo(line)
		}
		val eventType = event.getType()
		// Scala note: Use of == operator instead of "equals" method
		if (eventType == LineEvent.Type.OPEN) {
			Util.log(Level.FINE, "------- OPEN -------")
		}
		if (eventType == LineEvent.Type.START) {
			Util.log(Level.FINE, "------- START -------")
		}
		if (eventType == LineEvent.Type.STOP) {
			Util.log(Level.FINE, "------- STOP -------")
		}
		if (eventType == LineEvent.Type.CLOSE) {
			Util.log(Level.FINE, "------- CLOSE -------")
		}
	}

	def printInfo(line: Line) {
		val info = line.getLineInfo()
		Util.log(Level.FINE, "Line info: " + info)
		val controls = line.getControls()
		for (i <- 0 until controls.length) {
			val control = controls(i)
			Util.log(Level.FINE, "Control #" + i + ": " + control)
		}
	}
}

