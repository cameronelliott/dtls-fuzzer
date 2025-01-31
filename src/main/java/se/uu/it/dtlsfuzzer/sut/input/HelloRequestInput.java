package se.uu.it.dtlsfuzzer.sut.input;

import java.util.Arrays;

import javax.xml.bind.annotation.XmlAttribute;

import de.rub.nds.tlsattacker.core.protocol.message.HelloRequestMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ProtocolMessage;
import de.rub.nds.tlsattacker.core.state.State;
import se.uu.it.dtlsfuzzer.mapper.ExecutionContext;
import se.uu.it.dtlsfuzzer.sut.output.ModelOutputs;
import se.uu.it.dtlsfuzzer.sut.output.TlsOutput;

public class HelloRequestInput extends DtlsInput {
	
	@XmlAttribute(name = "resetSequenceNumber", required = false)
	private boolean resetSequenceNumber = true;
	
	@XmlAttribute(name = "disableOnRefusal", required = false)
	private boolean disableOnRefusal = true;
	
	@XmlAttribute(name = "retransmittedCHAsRefusal")
	private boolean retransmittedCHAsRefusal = true;
	
	private Integer origMsgSeqNum = 0;
	private byte[] clientRandom;

	public HelloRequestInput() {
		super("HELLO_REQUEST");
	}

	@Override
	public ProtocolMessage generateMessage(State state, ExecutionContext context) {
		if (resetSequenceNumber) {
			origMsgSeqNum = state.getTlsContext().getDtlsNextSendSequenceNumber();
			state.getTlsContext().setDtlsNextSendSequenceNumber(0);
		}
		if (retransmittedCHAsRefusal) {
			clientRandom = state.getTlsContext().getClientRandom();
		}
		return new HelloRequestMessage(state.getConfig());
	}
	
	@Override
	public void postSendDtlsUpdate(State state, ExecutionContext context) {
		context.updateRenegotiationIndex();
	}
	
	@Override
	public TlsOutput postReceiveUpdate(TlsOutput output, State state, ExecutionContext context) {
		if (!(ModelOutputs.hasClientHello(output))) {
			if (disableOnRefusal) {
				context.disableExecution();
			} else if (resetSequenceNumber) {
				state.getTlsContext().setDtlsNextSendSequenceNumber(origMsgSeqNum);
			}
		} else if ( disableOnRefusal && retransmittedCHAsRefusal && Arrays.equals(clientRandom, state.getTlsContext().getClientRandom())) {
			context.disableExecution();
		}
		return super.postReceiveUpdate(output, state, context);
	}
	
	@Override
	public TlsInputType getInputType() {
		return TlsInputType.HANDSHAKE;
	}

}
