package se.uu.it.modeltester.sut.io;

import javax.xml.bind.annotation.XmlAttribute;

import se.uu.it.modeltester.execute.AbstractInputExecutor;

/**
 * A TlsInput with a name, which is used as a unique identifier. 
 */
public abstract class NamedTlsInput extends TlsInput{
	
	protected NamedTlsInput(AbstractInputExecutor executor, String name) {
		super(executor);
		this.name = name;
	}

	/**
	 * The name with which the input can be referred 
	 */
	@XmlAttribute(name = "name", required = true)
	private String name;
	
	public String toString() {
		return name;
	}
	
	public final String getName() {
		return name;
	}

}