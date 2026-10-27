package org.commonlink.onchain.generated;

import io.reactivex.Flowable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint128;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.abi.datatypes.generated.Uint48;
import org.web3j.abi.datatypes.generated.Uint96;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple2;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/hyperledger/web3j/tree/main/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 4.12.2.
 */
@SuppressWarnings("rawtypes")
public class CommonLinkRegistry extends Contract {
    public static final String BINARY = "0x608060405234801561000f575f5ffd5b5060405161273938038061273983398101604081905261002e91610267565b83836001600160a01b03811661005d57604051636116401160e11b81525f600482015260240160405180910390fd5b600180546001600160d01b0316600160d01b65ffffffffffff8516021790556100865f82610136565b5050506001600160a01b0382166100b057604051630f1dc84560e21b815260040160405180910390fd5b6001600160a01b0381166100d75760405163c726084360e01b815260040160405180910390fd5b6101017ff996da754c790e95d5c7ca3330cfcad529487fe9d1d8edb7afc65076fdf9adb483610136565b5061012c7f65d7a28e3265b37a6474929f336521b332c1681b933f6cb9f3376673440d862a82610136565b50505050506102c4565b5f82610192575f61014f6002546001600160a01b031690565b6001600160a01b03161461017657604051631fe1e13d60e11b815260040160405180910390fd5b600280546001600160a01b0319166001600160a01b0384161790555b61019c83836101a5565b90505b92915050565b5f828152602081815260408083206001600160a01b038516845290915281205460ff16610245575f838152602081815260408083206001600160a01b03861684529091529020805460ff191660011790556101fd3390565b6001600160a01b0316826001600160a01b0316847f2f8788117e7eff1d82e926ec794901d17c78024a50270940304540a733656f0d60405160405180910390a450600161019f565b505f61019f565b80516001600160a01b0381168114610262575f5ffd5b919050565b5f5f5f5f6080858703121561027a575f5ffd5b845165ffffffffffff8116811461028f575f5ffd5b935061029d6020860161024c565b92506102ab6040860161024c565b91506102b96060860161024c565b905092959194509250565b612468806102d15f395ff3fe608060405234801561000f575f5ffd5b506004361061021e575f3560e01c806384e50a1c1161012a578063cabe0452116100b4578063d547741f11610079578063d547741f146109c1578063d602b9fd146109d4578063e2aa7102146109dc578063e63ab1e9146109f7578063ffa1ad7414610a1e575f5ffd5b8063cabe0452146107de578063cc8463c8146108b9578063ce4e07d9146108c1578063cefc14291461098b578063cf6eefb714610993575f5ffd5b806392ce70ed116100fa57806392ce70ed14610638578063a1eda53c146106b2578063a217fddf146106d9578063bea86581146106e0578063c8eb0c88146106f4575f5ffd5b806384e50a1c146105a257806384ef8ffc146105f85780638da5cb5b1461061d57806391d1485414610625575f5ffd5b80633f4ba83a116101ab578063649a5ec71161017b578063649a5ec7146104365780637354efa71461044957806375a65f1a1461045c5780637d69ba1f146105875780638456cb591461059a575f5ffd5b80633f4ba83a146102ff578063454cd16a146103075780635c975abb14610418578063634e93da14610423575f5ffd5b806313172b58116101f157806313172b5814610283578063248a9ca31461029657806327bc23f0146102c65780632f2ff15d146102d957806336568abe146102ec575f5ffd5b806301ffc9a714610222578063022d63fb1461024a5780630aa6220b146102665780630f49699e14610270575b5f5ffd5b610235610230366004612172565b610a53565b60405190151581526020015b60405180910390f35b620697805b60405165ffffffffffff9091168152602001610241565b61026e610a7c565b005b61026e61027e3660046121b4565b610a91565b61026e6102913660046121e7565b610c41565b6102b86102a4366004612207565b5f9081526020819052604090206001015490565b604051908152602001610241565b61026e6102d436600461221e565b610d24565b61026e6102e7366004612246565b610ec0565b61026e6102fa366004612246565b610eec565b61026e610f14565b6103b9610315366004612207565b6040805160c0810182525f80825260208201819052918101829052606081018290526080810182905260a0810191909152505f90815260076020908152604091829020825160c08101845281546001600160a01b0381168252600160a01b90046001600160601b031692810192909252600181015492820192909252600282015463ffffffff1660608201526003820154608082015260049091015460a082015290565b604051610241919081516001600160a01b031681526020808301516001600160601b0316908201526040808301519082015260608083015163ffffffff16908201526080808301519082015260a0918201519181019190915260c00190565b60035460ff16610235565b61026e610431366004612270565b610f26565b61026e610444366004612289565b610f39565b61026e6104573660046122c4565b610f4c565b61051561046a366004612207565b6040805160c0810182525f80825260208201819052918101829052606081018290526080810182905260a0810191909152505f908152600b6020908152604091829020825160c08101845290546001600160601b038116825263ffffffff600160601b8204811693830193909352600160801b8104831693820193909352600160a01b830482166060820152600160c01b830482166080820152600160e01b9092041660a082015290565b60405161024191905f60c0820190506001600160601b03835116825263ffffffff602084015116602083015263ffffffff604084015116604083015263ffffffff606084015116606083015263ffffffff608084015116608083015263ffffffff60a08401511660a083015292915050565b61026e610595366004612318565b6114c8565b61026e61176f565b6105e06105b036600461221e565b6001600160a01b03919091165f90815260096020908152604080832093835292905220546001600160801b031690565b6040516001600160801b039091168152602001610241565b6002546001600160a01b03165b6040516001600160a01b039091168152602001610241565b6106056117a1565b610235610633366004612246565b6117b9565b610691610646366004612270565b604080518082019091525f8082526020820152506001600160a01b03165f908152600460209081526040918290208251808401909352805483526001015463ffffffff169082015290565b604080518251815260209283015163ffffffff169281019290925201610241565b6106ba6117e1565b6040805165ffffffffffff938416815292909116602083015201610241565b6102b85f81565b6102b85f5160206124135f395f51905f5281565b61078c610702366004612270565b604080516080810182525f808252602082018190529181018290526060810191909152506001600160a01b03165f90815260086020908152604091829020825160808101845290546001600160801b038116825263ffffffff600160801b8204811693830193909352600160a01b8104831693820193909352600160c01b90920416606082015290565b60405161024191905f6080820190506001600160801b03835116825263ffffffff602084015116602083015263ffffffff604084015116604083015263ffffffff606084015116606083015292915050565b61086e6107ec366004612207565b604080516080810182525f808252602082018190529181018290526060810191909152505f90815260066020908152604091829020825160808101845281546001600160a01b0381168252600160a01b90046001600160601b031692810192909252600181015463ffffffff1692820192909252600290910154606082015290565b604051610241919081516001600160a01b031681526020808301516001600160601b03169082015260408083015163ffffffff16908201526060918201519181019190915260800190565b61024f611833565b6109496108cf366004612207565b604080516080810182525f808252602082018190529181018290526060810191909152505f908152600a602090815260409182902082516080810184528154815260018201546001600160601b03811693820193909352600160601b90920463ffffffff1692820192909252600290910154606082015290565b6040516102419190815181526020808301516001600160601b03169082015260408083015163ffffffff16908201526060918201519181019190915260800190565b61026e611891565b61099b6118d0565b604080516001600160a01b03909316835265ffffffffffff909116602083015201610241565b61026e6109cf366004612246565b6118f1565b61026e611919565b6109e4600181565b60405161ffff9091168152602001610241565b6102b87f65d7a28e3265b37a6474929f336521b332c1681b933f6cb9f3376673440d862a81565b610a46604051806040016040528060098152602001680332e302e302d6d76760bc1b81525081565b6040516102419190612352565b5f6001600160e01b031982166210e11560e71b1480610a765750610a768261192b565b92915050565b5f610a868161194f565b610a8e611959565b50565b610a99611965565b5f5160206124135f395f51905f52610ab08161194f565b83610ace57604051633925755160e11b815260040160405180910390fd5b5f8481526006602052604090205484906001600160a01b031615610b115760405163d9c9ecb960e01b8152600401610b0891815260200190565b60405180910390fd5b506001600160a01b0383165f90815260046020526040902060010154839063ffffffff16610b5e5760405163cfaae3e360e01b81526001600160a01b039091166004820152602401610b08565b5081610b7d57604051635b5c5f7960e11b815260040160405180910390fd5b604080516080810182526001600160a01b038581168083525f602080850182815263ffffffff42818116888a0181815260608a018d81528f885260068752968b9020995194516001600160601b0316600160a01b029490981693909317885595516001880180549190921663ffffffff1990911617905591516002909501949094558451878152938401529092909187917f62ca68d211749e65bb7a0e4f603de589fabd4bf1fd9ad011783bc0c2bee3cbe591015b60405180910390a35050505050565b610c49611965565b5f5160206124135f395f51905f52610c608161194f565b5f83815260066020526040902054839081906001600160a01b0316610c9b5760405163faed87b960e01b8152600401610b0891815260200190565b5082610cba57604051635b5c5f7960e11b815260040160405180910390fd5b5f848152600660209081526040918290206002810180549087905583518181529283018790524263ffffffff1683850152925190929187917fa84ca322cb316b896ef1c17b7e553c19db5ae4b930536ceb7c27e5bdc4caa6d09181900360600190a2505050505050565b610d2c611965565b5f5160206124135f395f51905f52610d438161194f565b6001600160a01b038316610d6a5760405163456db08160e01b815260040160405180910390fd5b81610d88576040516334c1f42f60e21b815260040160405180910390fd5b6001600160a01b0383165f90815260046020526040902060010154839063ffffffff1615610dd557604051637481f26b60e11b81526001600160a01b039091166004820152602401610b08565b505f828152600560205260409020546001600160a01b031682818015610e20576040516326b3def960e01b815260048101929092526001600160a01b03166024820152604401610b08565b50506040805180820182528481524263ffffffff81811660208085018281526001600160a01b038b165f818152600484528881209751885591516001909701805463ffffffff19169790951696909617909355888352600581529185902080546001600160a01b03191685179055935193845290928692917f885c7b58a8f0dc64e63ef94e5f621b87d3638a24d073ba1da87f242f30e391a59101610c32565b81610ede57604051631fe1e13d60e11b815260040160405180910390fd5b610ee88282611989565b5050565b81610f0a5760405163290b44fd60e21b815260040160405180910390fd5b610ee882826119b3565b5f610f1e8161194f565b610a8e611a59565b5f610f308161194f565b610ee882611aab565b5f610f438161194f565b610ee882611b1d565b610f54611965565b5f5160206124135f395f51905f52610f6b8161194f565b5f85815260066020526040902054859081906001600160a01b0316610fa65760405163faed87b960e01b8152600401610b0891815260200190565b5087610fc55760405163bc09d34760e01b815260040160405180910390fd5b5f8881526007602052604090205488906001600160a01b031615610fff576040516311576e8f60e11b8152600401610b0891815260200190565b506001600160a01b038716611025576040516148b160f01b815260040160405180910390fd5b846001600160601b03165f0361104e5760405163162908e360e11b815260040160405180910390fd5b8361106c5760405163b4479ffd60e01b815260040160405180910390fd5b8261108a57604051639ca53c0d60e01b815260040160405180910390fd5b5f60065f8881526020019081526020015f2090506040518060c00160405280896001600160a01b03168152602001876001600160601b031681526020018881526020014263ffffffff1681526020018681526020018581525060075f8b81526020019081526020015f205f820151815f015f6101000a8154816001600160a01b0302191690836001600160a01b031602179055506020820151815f0160146101000a8154816001600160601b0302191690836001600160601b03160217905550604082015181600101556060820151816002015f6101000a81548163ffffffff021916908363ffffffff1602179055506080820151816003015560a082015181600401559050505f815f0160149054906101000a90046001600160601b031690505f8782019050816001600160601b0316816001600160601b0316101589838a909192611204576040516390c9dc8d60e01b815260048101939093526001600160601b039182166024840152166044820152606401610b08565b505083546001600160601b03909216600160a01b026001600160a01b039283161784555089165f9081526008602090815260408083208054600984528285208d865290935292205491925063ffffffff600160c01b9091041615906001600160801b031615811561128c57825463ffffffff60c01b1916600160c01b4263ffffffff16021783555b82546001600160601b038a169084905f906112b19084906001600160801b031661239b565b92506101000a8154816001600160801b0302191690836001600160801b031602179055506001835f0160108282829054906101000a900463ffffffff166112f891906123ba565b92506101000a81548163ffffffff021916908363ffffffff160217905550801561135d578254600190849060149061133e908490600160a01b900463ffffffff166123ba565b92506101000a81548163ffffffff021916908363ffffffff1602179055505b6001600160a01b038b165f9081526009602090815260408083208d8452909152812080546001600160601b038c1692906113a19084906001600160801b031661239b565b92506101000a8154816001600160801b0302191690836001600160801b031602179055506113cf8a82611b8c565b8354604080518e81526001600160601b038c1660208201529081018a9052606081018990526001600160a01b03918216918c91908e16907fb32069e9fcbfc4a80516228c2e119a46b052b381de75b7386123658b1e76d9759060800160405180910390a4811561147e5760405163ffffffff421681526001600160a01b038c16907f968352eaec1cf3006fda21fe37c5c004429add6106ff33412a88cf776ed9d5b89060200160405180910390a25b80156114ba576040518a906001600160a01b038d16907ffeb17b547cef785d92dec6b967366250be5f333e4d4a0cd7d83e69d9a897337e905f90a35b505050505050505050505050565b6114d0611965565b5f5160206124135f395f51905f526114e78161194f565b5f84815260066020526040902054849081906001600160a01b03166115225760405163faed87b960e01b8152600401610b0891815260200190565b50856115415760405163678dc0c360e01b815260040160405180910390fd5b5f868152600a602052604090205486901561157257604051630a4de49b60e11b8152600401610b0891815260200190565b50836001600160601b03165f0361159c5760405163162908e360e11b815260040160405180910390fd5b826115ba57604051639ca53c0d60e01b815260040160405180910390fd5b604080516080810182528681526001600160601b03808716602080840191825263ffffffff42818116868801908152606087018b81525f8f8152600a86528981209851895595516001890180549351909516600160601b026fffffffffffffffffffffffffffffffff1990931690881617919091179092559051600290950194909455898252600b90529290922080549192909181169081880190899083908a9084168211156116975760405163e63b79fb60e01b815260048101939093526001600160601b039182166024840152166044820152606401610b08565b505083546bffffffffffffffffffffffff19166001600160601b03929092169190911780845560019250839150600c906116df908490600160601b900463ffffffff166123ba565b82546101009290920a63ffffffff81810219909316918316021790915582546001600160e01b0316600160e01b918516918202178355604080516001600160601b038a168152602081018990529081019190915288915089907f31dd68982cfb0c7364df4b6629026ec1eb534fab8264baa7dcb096b7a29150c69060600160405180910390a35050505050505050565b7f65d7a28e3265b37a6474929f336521b332c1681b933f6cb9f3376673440d862a6117998161194f565b610a8e611c40565b5f6117b46002546001600160a01b031690565b905090565b5f918252602082815260408084206001600160a01b0393909316845291905290205460ff1690565b6002545f90600160d01b900465ffffffffffff16801515801561180c57504265ffffffffffff821610155b611817575f5f61182b565b600254600160a01b900465ffffffffffff16815b915091509091565b6002545f90600160d01b900465ffffffffffff16801515801561185d57504265ffffffffffff8216105b61187857600154600160d01b900465ffffffffffff1661188b565b600254600160a01b900465ffffffffffff165b91505090565b5f61189a6118d0565b509050336001600160a01b038216146118c857604051636116401160e11b8152336004820152602401610b08565b610a8e611c7d565b6001546001600160a01b03811691600160a01b90910465ffffffffffff1690565b8161190f57604051631fe1e13d60e11b815260040160405180910390fd5b610ee88282611d13565b5f6119238161194f565b610a8e611d37565b5f6001600160e01b031982166318a4c3c360e11b1480610a765750610a7682611d41565b610a8e8133611d75565b6119635f5f611dae565b565b60035460ff16156119635760405163d93c066560e01b815260040160405180910390fd5b5f828152602081905260409020600101546119a38161194f565b6119ad8383611e6d565b50505050565b811580156119ce57506002546001600160a01b038281169116145b15611a4f575f5f6119dd6118d0565b90925090506001600160a01b0382161515806119ff575065ffffffffffff8116155b80611a1257504265ffffffffffff821610155b15611a3a576040516319ca5ebb60e01b815265ffffffffffff82166004820152602401610b08565b50506001805465ffffffffffff60a01b191690555b610ee88282611eda565b611a61611f12565b6003805460ff191690557f5db9ee0a495bf2e6ff9c91a7834c1ba4fdd244a5e8aa4e537bd38aeae4b073aa335b6040516001600160a01b03909116815260200160405180910390a1565b5f611ab4611833565b611abd42611f35565b611ac791906123d6565b9050611ad38282611f6b565b60405165ffffffffffff821681526001600160a01b038316907f3377dc44241e779dd06afab5b788a35ca5f3b778836e2990bdb26a2a4b2e5ed69060200160405180910390a25050565b5f611b2782611fe8565b611b3042611f35565b611b3a91906123d6565b9050611b468282611dae565b6040805165ffffffffffff8085168252831660208201527ff1038c18cf84a56e432fdbfaf746924b7ea511dfe03a6506a0ceba4888788d9b910160405180910390a15050565b5f828152600b6020526040902080546001908290601090611bbb908490600160801b900463ffffffff166123ba565b92506101000a81548163ffffffff021916908363ffffffff1602179055508115611c205780546001908290601490611c01908490600160a01b900463ffffffff166123ba565b92506101000a81548163ffffffff021916908363ffffffff1602179055505b805463ffffffff60c01b1916600160c01b4263ffffffff16021790555050565b611c48611965565b6003805460ff191660011790557f62e78cea01bee320cd4e420270b5ea74000d11b0c9f74754ebdbfc544b05a258611a8e3390565b5f5f611c876118d0565b91509150611c9c8165ffffffffffff16151590565b1580611cb057504265ffffffffffff821610155b15611cd8576040516319ca5ebb60e01b815265ffffffffffff82166004820152602401610b08565b611cf35f611cee6002546001600160a01b031690565b61202f565b50611cfe5f83611e6d565b5050600180546001600160d01b031916905550565b5f82815260208190526040902060010154611d2d8161194f565b6119ad838361202f565b6119635f5f611f6b565b5f6001600160e01b03198216637965db0b60e01b1480610a7657506301ffc9a760e01b6001600160e01b0319831614610a76565b611d7f82826117b9565b610ee85760405163e2517d3f60e01b81526001600160a01b038216600482015260248101839052604401610b08565b600254600160d01b900465ffffffffffff168015611e30574265ffffffffffff82161015611e0757600254600180546001600160d01b0316600160a01b90920465ffffffffffff16600160d01b02919091179055611e30565b6040517f2b1fa2edafe6f7b9e97c1a9e0c3660e645beb2dcaa2d45bdbf9beaf5472e1ec5905f90a15b50600280546001600160a01b0316600160a01b65ffffffffffff948516026001600160d01b031617600160d01b9290931691909102919091179055565b5f82611ec9575f611e866002546001600160a01b031690565b6001600160a01b031614611ead57604051631fe1e13d60e11b815260040160405180910390fd5b600280546001600160a01b0319166001600160a01b0384161790555b611ed3838361206b565b9392505050565b6001600160a01b0381163314611f035760405163334bd91960e11b815260040160405180910390fd5b611f0d828261202f565b505050565b60035460ff1661196357604051638dfc202b60e01b815260040160405180910390fd5b5f65ffffffffffff821115611f67576040516306dfcc6560e41b81526030600482015260248101839052604401610b08565b5090565b5f611f746118d0565b6001805465ffffffffffff8616600160a01b026001600160d01b03199091166001600160a01b038816171790559150611fb690508165ffffffffffff16151590565b15611f0d576040517f8886ebfc4259abdbc16601dd8fb5678e54878f47b3c34836cfc51154a9605109905f90a1505050565b5f5f611ff2611833565b90508065ffffffffffff168365ffffffffffff161161201a5761201583826123f4565b611ed3565b611ed365ffffffffffff8416620697806120fa565b5f8215801561204b57506002546001600160a01b038381169116145b1561206157600280546001600160a01b03191690555b611ed38383612109565b5f61207683836117b9565b6120f3575f838152602081815260408083206001600160a01b03861684529091529020805460ff191660011790556120ab3390565b6001600160a01b0316826001600160a01b0316847f2f8788117e7eff1d82e926ec794901d17c78024a50270940304540a733656f0d60405160405180910390a4506001610a76565b505f610a76565b5f828218828410028218611ed3565b5f61211483836117b9565b156120f3575f838152602081815260408083206001600160a01b0386168085529252808320805460ff1916905551339286917ff6391f5c32d9c69d2a47ea670b442974b53935d1edc7fd64eb21e047a839171b9190a4506001610a76565b5f60208284031215612182575f5ffd5b81356001600160e01b031981168114611ed3575f5ffd5b80356001600160a01b03811681146121af575f5ffd5b919050565b5f5f5f606084860312156121c6575f5ffd5b833592506121d660208501612199565b929592945050506040919091013590565b5f5f604083850312156121f8575f5ffd5b50508035926020909101359150565b5f60208284031215612217575f5ffd5b5035919050565b5f5f6040838503121561222f575f5ffd5b61223883612199565b946020939093013593505050565b5f5f60408385031215612257575f5ffd5b8235915061226760208401612199565b90509250929050565b5f60208284031215612280575f5ffd5b611ed382612199565b5f60208284031215612299575f5ffd5b813565ffffffffffff81168114611ed3575f5ffd5b80356001600160601b03811681146121af575f5ffd5b5f5f5f5f5f5f60c087890312156122d9575f5ffd5b863595506122e960208801612199565b9450604087013593506122fe606088016122ae565b9598949750929560808101359460a0909101359350915050565b5f5f5f5f6080858703121561232b575f5ffd5b8435935060208501359250612342604086016122ae565b9396929550929360600135925050565b602081525f82518060208401528060208501604085015e5f604082850101526040601f19601f83011684010191505092915050565b634e487b7160e01b5f52601160045260245ffd5b6001600160801b038181168382160190811115610a7657610a76612387565b63ffffffff8181168382160190811115610a7657610a76612387565b65ffffffffffff8181168382160190811115610a7657610a76612387565b65ffffffffffff8281168282160390811115610a7657610a7661238756fef996da754c790e95d5c7ca3330cfcad529487fe9d1d8edb7afc65076fdf9adb4a2646970667358221220c1b50f119b36c425f1e86b8e1078dea2873a7319aabf189202bf17de6a0dfca064736f6c634300081e0033";

    private static String librariesLinkedBinary;

    public static final String FUNC_DEFAULT_ADMIN_ROLE = "DEFAULT_ADMIN_ROLE";

    public static final String FUNC_PAUSER_ROLE = "PAUSER_ROLE";

    public static final String FUNC_READER_VERSION = "READER_VERSION";

    public static final String FUNC_RECORDER_ROLE = "RECORDER_ROLE";

    public static final String FUNC_VERSION = "VERSION";

    public static final String FUNC_ACCEPTDEFAULTADMINTRANSFER = "acceptDefaultAdminTransfer";

    public static final String FUNC_BEGINDEFAULTADMINTRANSFER = "beginDefaultAdminTransfer";

    public static final String FUNC_CANCELDEFAULTADMINTRANSFER = "cancelDefaultAdminTransfer";

    public static final String FUNC_CHANGEDEFAULTADMINDELAY = "changeDefaultAdminDelay";

    public static final String FUNC_DEFAULTADMIN = "defaultAdmin";

    public static final String FUNC_DEFAULTADMINDELAY = "defaultAdminDelay";

    public static final String FUNC_DEFAULTADMINDELAYINCREASEWAIT = "defaultAdminDelayIncreaseWait";

    public static final String FUNC_GETASSOCIATION = "getAssociation";

    public static final String FUNC_GETCAMPAIGN = "getCampaign";

    public static final String FUNC_GETCAMPAIGNSTATS = "getCampaignStats";

    public static final String FUNC_GETDONATION = "getDonation";

    public static final String FUNC_GETDONORCAMPAIGNTOTAL = "getDonorCampaignTotal";

    public static final String FUNC_GETDONORSTATS = "getDonorStats";

    public static final String FUNC_GETPAYOUT = "getPayout";

    public static final String FUNC_GETROLEADMIN = "getRoleAdmin";

    public static final String FUNC_GRANTROLE = "grantRole";

    public static final String FUNC_HASROLE = "hasRole";

    public static final String FUNC_OWNER = "owner";

    public static final String FUNC_PAUSE = "pause";

    public static final String FUNC_PAUSED = "paused";

    public static final String FUNC_PENDINGDEFAULTADMIN = "pendingDefaultAdmin";

    public static final String FUNC_PENDINGDEFAULTADMINDELAY = "pendingDefaultAdminDelay";

    public static final String FUNC_RECORDDONATION = "recordDonation";

    public static final String FUNC_RECORDPAYOUT = "recordPayout";

    public static final String FUNC_REGISTERASSOCIATION = "registerAssociation";

    public static final String FUNC_REGISTERCAMPAIGN = "registerCampaign";

    public static final String FUNC_RENOUNCEROLE = "renounceRole";

    public static final String FUNC_REVOKEROLE = "revokeRole";

    public static final String FUNC_ROLLBACKDEFAULTADMINDELAY = "rollbackDefaultAdminDelay";

    public static final String FUNC_SUPPORTSINTERFACE = "supportsInterface";

    public static final String FUNC_UNPAUSE = "unpause";

    public static final String FUNC_UPDATECAMPAIGNBUDGET = "updateCampaignBudget";

    public static final Event ASSOCIATIONREGISTERED_EVENT = new Event("AssociationRegistered", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Bytes32>(true) {}, new TypeReference<Uint32>() {}));
    ;

    public static final Event CAMPAIGNBUDGETUPDATED_EVENT = new Event("CampaignBudgetUpdated", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>(true) {}, new TypeReference<Bytes32>() {}, new TypeReference<Bytes32>() {}, new TypeReference<Uint32>() {}));
    ;

    public static final Event CAMPAIGNREGISTERED_EVENT = new Event("CampaignRegistered", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Bytes32>() {}, new TypeReference<Uint32>() {}));
    ;

    public static final Event DEFAULTADMINDELAYCHANGECANCELED_EVENT = new Event("DefaultAdminDelayChangeCanceled", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final Event DEFAULTADMINDELAYCHANGESCHEDULED_EVENT = new Event("DefaultAdminDelayChangeScheduled", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint48>() {}, new TypeReference<Uint48>() {}));
    ;

    public static final Event DEFAULTADMINTRANSFERCANCELED_EVENT = new Event("DefaultAdminTransferCanceled", 
            Arrays.<TypeReference<?>>asList());
    ;

    public static final Event DEFAULTADMINTRANSFERSCHEDULED_EVENT = new Event("DefaultAdminTransferScheduled", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint48>() {}));
    ;

    public static final Event DONATIONRECORDED_EVENT = new Event("DonationRecorded", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Bytes32>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Bytes32>() {}, new TypeReference<Uint96>() {}, new TypeReference<Bytes32>() {}, new TypeReference<Bytes32>() {}));
    ;

    public static final Event DONORFIRSTDONATION_EVENT = new Event("DonorFirstDonation", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint32>() {}));
    ;

    public static final Event DONORJOINEDCAMPAIGN_EVENT = new Event("DonorJoinedCampaign", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Bytes32>(true) {}));
    ;

    public static final Event PAUSED_EVENT = new Event("Paused", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    ;

    public static final Event PAYOUTRECORDED_EVENT = new Event("PayoutRecorded", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>(true) {}, new TypeReference<Bytes32>(true) {}, new TypeReference<Uint96>() {}, new TypeReference<Bytes32>() {}, new TypeReference<Uint32>() {}));
    ;

    public static final Event ROLEADMINCHANGED_EVENT = new Event("RoleAdminChanged", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>(true) {}, new TypeReference<Bytes32>(true) {}, new TypeReference<Bytes32>(true) {}));
    ;

    public static final Event ROLEGRANTED_EVENT = new Event("RoleGranted", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}));
    ;

    public static final Event ROLEREVOKED_EVENT = new Event("RoleRevoked", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}));
    ;

    public static final Event UNPAUSED_EVENT = new Event("Unpaused", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    ;

    protected static final HashMap<String, String> _addresses;

    static {
        _addresses = new HashMap<String, String>();
    }

    @Deprecated
    protected CommonLinkRegistry(String contractAddress, Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected CommonLinkRegistry(String contractAddress, Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected CommonLinkRegistry(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected CommonLinkRegistry(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public RemoteFunctionCall<byte[]> DEFAULT_ADMIN_ROLE() {
        final Function function = new Function(FUNC_DEFAULT_ADMIN_ROLE, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    public RemoteFunctionCall<byte[]> PAUSER_ROLE() {
        final Function function = new Function(FUNC_PAUSER_ROLE, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    public RemoteFunctionCall<BigInteger> READER_VERSION() {
        final Function function = new Function(FUNC_READER_VERSION, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint16>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<byte[]> RECORDER_ROLE() {
        final Function function = new Function(FUNC_RECORDER_ROLE, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    public RemoteFunctionCall<String> VERSION() {
        final Function function = new Function(FUNC_VERSION, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<TransactionReceipt> acceptDefaultAdminTransfer() {
        final Function function = new Function(
                FUNC_ACCEPTDEFAULTADMINTRANSFER, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> beginDefaultAdminTransfer(String newAdmin) {
        final Function function = new Function(
                FUNC_BEGINDEFAULTADMINTRANSFER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(newAdmin)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> cancelDefaultAdminTransfer() {
        final Function function = new Function(
                FUNC_CANCELDEFAULTADMINTRANSFER, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> changeDefaultAdminDelay(BigInteger newDelay) {
        final Function function = new Function(
                FUNC_CHANGEDEFAULTADMINDELAY, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint48(newDelay)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<String> defaultAdmin() {
        final Function function = new Function(FUNC_DEFAULTADMIN, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<BigInteger> defaultAdminDelay() {
        final Function function = new Function(FUNC_DEFAULTADMINDELAY, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint48>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> defaultAdminDelayIncreaseWait() {
        final Function function = new Function(FUNC_DEFAULTADMINDELAYINCREASEWAIT, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint48>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<Association> getAssociation(String association) {
        final Function function = new Function(FUNC_GETASSOCIATION, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(association)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Association>() {}));
        return executeRemoteCallSingleValueReturn(function, Association.class);
    }

    public RemoteFunctionCall<Campaign> getCampaign(byte[] campaignId) {
        final Function function = new Function(FUNC_GETCAMPAIGN, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(campaignId)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Campaign>() {}));
        return executeRemoteCallSingleValueReturn(function, Campaign.class);
    }

    public RemoteFunctionCall<CampaignStats> getCampaignStats(byte[] campaignId) {
        final Function function = new Function(FUNC_GETCAMPAIGNSTATS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(campaignId)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<CampaignStats>() {}));
        return executeRemoteCallSingleValueReturn(function, CampaignStats.class);
    }

    public RemoteFunctionCall<Donation> getDonation(byte[] donationId) {
        final Function function = new Function(FUNC_GETDONATION, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(donationId)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Donation>() {}));
        return executeRemoteCallSingleValueReturn(function, Donation.class);
    }

    public RemoteFunctionCall<BigInteger> getDonorCampaignTotal(String donor, byte[] campaignId) {
        final Function function = new Function(FUNC_GETDONORCAMPAIGNTOTAL, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(donor), 
                new org.web3j.abi.datatypes.generated.Bytes32(campaignId)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint128>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<DonorStats> getDonorStats(String donor) {
        final Function function = new Function(FUNC_GETDONORSTATS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(donor)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DonorStats>() {}));
        return executeRemoteCallSingleValueReturn(function, DonorStats.class);
    }

    public RemoteFunctionCall<Payout> getPayout(byte[] payoutId) {
        final Function function = new Function(FUNC_GETPAYOUT, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(payoutId)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Payout>() {}));
        return executeRemoteCallSingleValueReturn(function, Payout.class);
    }

    public RemoteFunctionCall<byte[]> getRoleAdmin(byte[] role) {
        final Function function = new Function(FUNC_GETROLEADMIN, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(role)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    public RemoteFunctionCall<TransactionReceipt> grantRole(byte[] role, String account) {
        final Function function = new Function(
                FUNC_GRANTROLE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(role), 
                new org.web3j.abi.datatypes.Address(account)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<Boolean> hasRole(byte[] role, String account) {
        final Function function = new Function(FUNC_HASROLE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(role), 
                new org.web3j.abi.datatypes.Address(account)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<String> owner() {
        final Function function = new Function(FUNC_OWNER, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<TransactionReceipt> pause() {
        final Function function = new Function(
                FUNC_PAUSE, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<Boolean> paused() {
        final Function function = new Function(FUNC_PAUSED, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<Tuple2<String, BigInteger>> pendingDefaultAdmin() {
        final Function function = new Function(FUNC_PENDINGDEFAULTADMIN, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Uint48>() {}));
        return new RemoteFunctionCall<Tuple2<String, BigInteger>>(function,
                new Callable<Tuple2<String, BigInteger>>() {
                    @Override
                    public Tuple2<String, BigInteger> call() throws Exception {
                        List<Type> results = executeCallMultipleValueReturn(function);
                        return new Tuple2<String, BigInteger>(
                                (String) results.get(0).getValue(), 
                                (BigInteger) results.get(1).getValue());
                    }
                });
    }

    public RemoteFunctionCall<Tuple2<BigInteger, BigInteger>> pendingDefaultAdminDelay() {
        final Function function = new Function(FUNC_PENDINGDEFAULTADMINDELAY, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint48>() {}, new TypeReference<Uint48>() {}));
        return new RemoteFunctionCall<Tuple2<BigInteger, BigInteger>>(function,
                new Callable<Tuple2<BigInteger, BigInteger>>() {
                    @Override
                    public Tuple2<BigInteger, BigInteger> call() throws Exception {
                        List<Type> results = executeCallMultipleValueReturn(function);
                        return new Tuple2<BigInteger, BigInteger>(
                                (BigInteger) results.get(0).getValue(), 
                                (BigInteger) results.get(1).getValue());
                    }
                });
    }

    public RemoteFunctionCall<TransactionReceipt> recordDonation(byte[] donationId, String donor,
            byte[] campaignId, BigInteger amount, byte[] receiptHash, byte[] txRef) {
        final Function function = new Function(
                FUNC_RECORDDONATION, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(donationId), 
                new org.web3j.abi.datatypes.Address(donor), 
                new org.web3j.abi.datatypes.generated.Bytes32(campaignId), 
                new org.web3j.abi.datatypes.generated.Uint96(amount), 
                new org.web3j.abi.datatypes.generated.Bytes32(receiptHash), 
                new org.web3j.abi.datatypes.generated.Bytes32(txRef)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> recordPayout(byte[] payoutId, byte[] campaignId,
            BigInteger amountCents, byte[] txRef) {
        final Function function = new Function(
                FUNC_RECORDPAYOUT, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(payoutId), 
                new org.web3j.abi.datatypes.generated.Bytes32(campaignId), 
                new org.web3j.abi.datatypes.generated.Uint96(amountCents), 
                new org.web3j.abi.datatypes.generated.Bytes32(txRef)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> registerAssociation(String association,
            byte[] sirenHash) {
        final Function function = new Function(
                FUNC_REGISTERASSOCIATION, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(association), 
                new org.web3j.abi.datatypes.generated.Bytes32(sirenHash)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> registerCampaign(byte[] campaignId,
            String association, byte[] budgetHash) {
        final Function function = new Function(
                FUNC_REGISTERCAMPAIGN, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(campaignId), 
                new org.web3j.abi.datatypes.Address(association), 
                new org.web3j.abi.datatypes.generated.Bytes32(budgetHash)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> renounceRole(byte[] role, String account) {
        final Function function = new Function(
                FUNC_RENOUNCEROLE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(role), 
                new org.web3j.abi.datatypes.Address(account)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> revokeRole(byte[] role, String account) {
        final Function function = new Function(
                FUNC_REVOKEROLE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(role), 
                new org.web3j.abi.datatypes.Address(account)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> rollbackDefaultAdminDelay() {
        final Function function = new Function(
                FUNC_ROLLBACKDEFAULTADMINDELAY, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<Boolean> supportsInterface(byte[] interfaceId) {
        final Function function = new Function(FUNC_SUPPORTSINTERFACE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes4(interfaceId)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<TransactionReceipt> unpause() {
        final Function function = new Function(
                FUNC_UNPAUSE, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> updateCampaignBudget(byte[] campaignId,
            byte[] newBudgetHash) {
        final Function function = new Function(
                FUNC_UPDATECAMPAIGNBUDGET, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(campaignId), 
                new org.web3j.abi.datatypes.generated.Bytes32(newBudgetHash)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public static List<AssociationRegisteredEventResponse> getAssociationRegisteredEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(ASSOCIATIONREGISTERED_EVENT, transactionReceipt);
        ArrayList<AssociationRegisteredEventResponse> responses = new ArrayList<AssociationRegisteredEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            AssociationRegisteredEventResponse typedResponse = new AssociationRegisteredEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.association = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.sirenHash = (byte[]) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.registeredAt = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static AssociationRegisteredEventResponse getAssociationRegisteredEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(ASSOCIATIONREGISTERED_EVENT, log);
        AssociationRegisteredEventResponse typedResponse = new AssociationRegisteredEventResponse();
        typedResponse.log = log;
        typedResponse.association = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.sirenHash = (byte[]) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.registeredAt = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<AssociationRegisteredEventResponse> associationRegisteredEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getAssociationRegisteredEventFromLog(log));
    }

    public Flowable<AssociationRegisteredEventResponse> associationRegisteredEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ASSOCIATIONREGISTERED_EVENT));
        return associationRegisteredEventFlowable(filter);
    }

    public static List<CampaignBudgetUpdatedEventResponse> getCampaignBudgetUpdatedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(CAMPAIGNBUDGETUPDATED_EVENT, transactionReceipt);
        ArrayList<CampaignBudgetUpdatedEventResponse> responses = new ArrayList<CampaignBudgetUpdatedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            CampaignBudgetUpdatedEventResponse typedResponse = new CampaignBudgetUpdatedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.campaignId = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.oldBudgetHash = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.newBudgetHash = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static CampaignBudgetUpdatedEventResponse getCampaignBudgetUpdatedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(CAMPAIGNBUDGETUPDATED_EVENT, log);
        CampaignBudgetUpdatedEventResponse typedResponse = new CampaignBudgetUpdatedEventResponse();
        typedResponse.log = log;
        typedResponse.campaignId = (byte[]) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.oldBudgetHash = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.newBudgetHash = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
        typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
        return typedResponse;
    }

    public Flowable<CampaignBudgetUpdatedEventResponse> campaignBudgetUpdatedEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getCampaignBudgetUpdatedEventFromLog(log));
    }

    public Flowable<CampaignBudgetUpdatedEventResponse> campaignBudgetUpdatedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(CAMPAIGNBUDGETUPDATED_EVENT));
        return campaignBudgetUpdatedEventFlowable(filter);
    }

    public static List<CampaignRegisteredEventResponse> getCampaignRegisteredEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(CAMPAIGNREGISTERED_EVENT, transactionReceipt);
        ArrayList<CampaignRegisteredEventResponse> responses = new ArrayList<CampaignRegisteredEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            CampaignRegisteredEventResponse typedResponse = new CampaignRegisteredEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.campaignId = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.association = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.budgetHash = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.registeredAt = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static CampaignRegisteredEventResponse getCampaignRegisteredEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(CAMPAIGNREGISTERED_EVENT, log);
        CampaignRegisteredEventResponse typedResponse = new CampaignRegisteredEventResponse();
        typedResponse.log = log;
        typedResponse.campaignId = (byte[]) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.association = (String) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.budgetHash = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.registeredAt = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        return typedResponse;
    }

    public Flowable<CampaignRegisteredEventResponse> campaignRegisteredEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getCampaignRegisteredEventFromLog(log));
    }

    public Flowable<CampaignRegisteredEventResponse> campaignRegisteredEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(CAMPAIGNREGISTERED_EVENT));
        return campaignRegisteredEventFlowable(filter);
    }

    public static List<DefaultAdminDelayChangeCanceledEventResponse> getDefaultAdminDelayChangeCanceledEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(DEFAULTADMINDELAYCHANGECANCELED_EVENT, transactionReceipt);
        ArrayList<DefaultAdminDelayChangeCanceledEventResponse> responses = new ArrayList<DefaultAdminDelayChangeCanceledEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DefaultAdminDelayChangeCanceledEventResponse typedResponse = new DefaultAdminDelayChangeCanceledEventResponse();
            typedResponse.log = eventValues.getLog();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static DefaultAdminDelayChangeCanceledEventResponse getDefaultAdminDelayChangeCanceledEventFromLog(
            Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(DEFAULTADMINDELAYCHANGECANCELED_EVENT, log);
        DefaultAdminDelayChangeCanceledEventResponse typedResponse = new DefaultAdminDelayChangeCanceledEventResponse();
        typedResponse.log = log;
        return typedResponse;
    }

    public Flowable<DefaultAdminDelayChangeCanceledEventResponse> defaultAdminDelayChangeCanceledEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getDefaultAdminDelayChangeCanceledEventFromLog(log));
    }

    public Flowable<DefaultAdminDelayChangeCanceledEventResponse> defaultAdminDelayChangeCanceledEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DEFAULTADMINDELAYCHANGECANCELED_EVENT));
        return defaultAdminDelayChangeCanceledEventFlowable(filter);
    }

    public static List<DefaultAdminDelayChangeScheduledEventResponse> getDefaultAdminDelayChangeScheduledEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(DEFAULTADMINDELAYCHANGESCHEDULED_EVENT, transactionReceipt);
        ArrayList<DefaultAdminDelayChangeScheduledEventResponse> responses = new ArrayList<DefaultAdminDelayChangeScheduledEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DefaultAdminDelayChangeScheduledEventResponse typedResponse = new DefaultAdminDelayChangeScheduledEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.newDelay = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.effectSchedule = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static DefaultAdminDelayChangeScheduledEventResponse getDefaultAdminDelayChangeScheduledEventFromLog(
            Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(DEFAULTADMINDELAYCHANGESCHEDULED_EVENT, log);
        DefaultAdminDelayChangeScheduledEventResponse typedResponse = new DefaultAdminDelayChangeScheduledEventResponse();
        typedResponse.log = log;
        typedResponse.newDelay = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.effectSchedule = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        return typedResponse;
    }

    public Flowable<DefaultAdminDelayChangeScheduledEventResponse> defaultAdminDelayChangeScheduledEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getDefaultAdminDelayChangeScheduledEventFromLog(log));
    }

    public Flowable<DefaultAdminDelayChangeScheduledEventResponse> defaultAdminDelayChangeScheduledEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DEFAULTADMINDELAYCHANGESCHEDULED_EVENT));
        return defaultAdminDelayChangeScheduledEventFlowable(filter);
    }

    public static List<DefaultAdminTransferCanceledEventResponse> getDefaultAdminTransferCanceledEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(DEFAULTADMINTRANSFERCANCELED_EVENT, transactionReceipt);
        ArrayList<DefaultAdminTransferCanceledEventResponse> responses = new ArrayList<DefaultAdminTransferCanceledEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DefaultAdminTransferCanceledEventResponse typedResponse = new DefaultAdminTransferCanceledEventResponse();
            typedResponse.log = eventValues.getLog();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static DefaultAdminTransferCanceledEventResponse getDefaultAdminTransferCanceledEventFromLog(
            Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(DEFAULTADMINTRANSFERCANCELED_EVENT, log);
        DefaultAdminTransferCanceledEventResponse typedResponse = new DefaultAdminTransferCanceledEventResponse();
        typedResponse.log = log;
        return typedResponse;
    }

    public Flowable<DefaultAdminTransferCanceledEventResponse> defaultAdminTransferCanceledEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getDefaultAdminTransferCanceledEventFromLog(log));
    }

    public Flowable<DefaultAdminTransferCanceledEventResponse> defaultAdminTransferCanceledEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DEFAULTADMINTRANSFERCANCELED_EVENT));
        return defaultAdminTransferCanceledEventFlowable(filter);
    }

    public static List<DefaultAdminTransferScheduledEventResponse> getDefaultAdminTransferScheduledEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(DEFAULTADMINTRANSFERSCHEDULED_EVENT, transactionReceipt);
        ArrayList<DefaultAdminTransferScheduledEventResponse> responses = new ArrayList<DefaultAdminTransferScheduledEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DefaultAdminTransferScheduledEventResponse typedResponse = new DefaultAdminTransferScheduledEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.newAdmin = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.acceptSchedule = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static DefaultAdminTransferScheduledEventResponse getDefaultAdminTransferScheduledEventFromLog(
            Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(DEFAULTADMINTRANSFERSCHEDULED_EVENT, log);
        DefaultAdminTransferScheduledEventResponse typedResponse = new DefaultAdminTransferScheduledEventResponse();
        typedResponse.log = log;
        typedResponse.newAdmin = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.acceptSchedule = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<DefaultAdminTransferScheduledEventResponse> defaultAdminTransferScheduledEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getDefaultAdminTransferScheduledEventFromLog(log));
    }

    public Flowable<DefaultAdminTransferScheduledEventResponse> defaultAdminTransferScheduledEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DEFAULTADMINTRANSFERSCHEDULED_EVENT));
        return defaultAdminTransferScheduledEventFlowable(filter);
    }

    public static List<DonationRecordedEventResponse> getDonationRecordedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(DONATIONRECORDED_EVENT, transactionReceipt);
        ArrayList<DonationRecordedEventResponse> responses = new ArrayList<DonationRecordedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DonationRecordedEventResponse typedResponse = new DonationRecordedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.donor = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.campaignId = (byte[]) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.association = (String) eventValues.getIndexedValues().get(2).getValue();
            typedResponse.donationId = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.receiptHash = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
            typedResponse.txRef = (byte[]) eventValues.getNonIndexedValues().get(3).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static DonationRecordedEventResponse getDonationRecordedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(DONATIONRECORDED_EVENT, log);
        DonationRecordedEventResponse typedResponse = new DonationRecordedEventResponse();
        typedResponse.log = log;
        typedResponse.donor = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.campaignId = (byte[]) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.association = (String) eventValues.getIndexedValues().get(2).getValue();
        typedResponse.donationId = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
        typedResponse.receiptHash = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
        typedResponse.txRef = (byte[]) eventValues.getNonIndexedValues().get(3).getValue();
        return typedResponse;
    }

    public Flowable<DonationRecordedEventResponse> donationRecordedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getDonationRecordedEventFromLog(log));
    }

    public Flowable<DonationRecordedEventResponse> donationRecordedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DONATIONRECORDED_EVENT));
        return donationRecordedEventFlowable(filter);
    }

    public static List<DonorFirstDonationEventResponse> getDonorFirstDonationEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(DONORFIRSTDONATION_EVENT, transactionReceipt);
        ArrayList<DonorFirstDonationEventResponse> responses = new ArrayList<DonorFirstDonationEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DonorFirstDonationEventResponse typedResponse = new DonorFirstDonationEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.donor = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static DonorFirstDonationEventResponse getDonorFirstDonationEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(DONORFIRSTDONATION_EVENT, log);
        DonorFirstDonationEventResponse typedResponse = new DonorFirstDonationEventResponse();
        typedResponse.log = log;
        typedResponse.donor = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<DonorFirstDonationEventResponse> donorFirstDonationEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getDonorFirstDonationEventFromLog(log));
    }

    public Flowable<DonorFirstDonationEventResponse> donorFirstDonationEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DONORFIRSTDONATION_EVENT));
        return donorFirstDonationEventFlowable(filter);
    }

    public static List<DonorJoinedCampaignEventResponse> getDonorJoinedCampaignEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(DONORJOINEDCAMPAIGN_EVENT, transactionReceipt);
        ArrayList<DonorJoinedCampaignEventResponse> responses = new ArrayList<DonorJoinedCampaignEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DonorJoinedCampaignEventResponse typedResponse = new DonorJoinedCampaignEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.donor = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.campaignId = (byte[]) eventValues.getIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static DonorJoinedCampaignEventResponse getDonorJoinedCampaignEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(DONORJOINEDCAMPAIGN_EVENT, log);
        DonorJoinedCampaignEventResponse typedResponse = new DonorJoinedCampaignEventResponse();
        typedResponse.log = log;
        typedResponse.donor = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.campaignId = (byte[]) eventValues.getIndexedValues().get(1).getValue();
        return typedResponse;
    }

    public Flowable<DonorJoinedCampaignEventResponse> donorJoinedCampaignEventFlowable(
            EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getDonorJoinedCampaignEventFromLog(log));
    }

    public Flowable<DonorJoinedCampaignEventResponse> donorJoinedCampaignEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DONORJOINEDCAMPAIGN_EVENT));
        return donorJoinedCampaignEventFlowable(filter);
    }

    public static List<PausedEventResponse> getPausedEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(PAUSED_EVENT, transactionReceipt);
        ArrayList<PausedEventResponse> responses = new ArrayList<PausedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            PausedEventResponse typedResponse = new PausedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.account = (String) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static PausedEventResponse getPausedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(PAUSED_EVENT, log);
        PausedEventResponse typedResponse = new PausedEventResponse();
        typedResponse.log = log;
        typedResponse.account = (String) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<PausedEventResponse> pausedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getPausedEventFromLog(log));
    }

    public Flowable<PausedEventResponse> pausedEventFlowable(DefaultBlockParameter startBlock,
            DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(PAUSED_EVENT));
        return pausedEventFlowable(filter);
    }

    public static List<PayoutRecordedEventResponse> getPayoutRecordedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(PAYOUTRECORDED_EVENT, transactionReceipt);
        ArrayList<PayoutRecordedEventResponse> responses = new ArrayList<PayoutRecordedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            PayoutRecordedEventResponse typedResponse = new PayoutRecordedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.payoutId = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.campaignId = (byte[]) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.txRef = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static PayoutRecordedEventResponse getPayoutRecordedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(PAYOUTRECORDED_EVENT, log);
        PayoutRecordedEventResponse typedResponse = new PayoutRecordedEventResponse();
        typedResponse.log = log;
        typedResponse.payoutId = (byte[]) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.campaignId = (byte[]) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        typedResponse.txRef = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
        typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
        return typedResponse;
    }

    public Flowable<PayoutRecordedEventResponse> payoutRecordedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getPayoutRecordedEventFromLog(log));
    }

    public Flowable<PayoutRecordedEventResponse> payoutRecordedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(PAYOUTRECORDED_EVENT));
        return payoutRecordedEventFlowable(filter);
    }

    public static List<RoleAdminChangedEventResponse> getRoleAdminChangedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(ROLEADMINCHANGED_EVENT, transactionReceipt);
        ArrayList<RoleAdminChangedEventResponse> responses = new ArrayList<RoleAdminChangedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            RoleAdminChangedEventResponse typedResponse = new RoleAdminChangedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.role = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.previousAdminRole = (byte[]) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.newAdminRole = (byte[]) eventValues.getIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static RoleAdminChangedEventResponse getRoleAdminChangedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(ROLEADMINCHANGED_EVENT, log);
        RoleAdminChangedEventResponse typedResponse = new RoleAdminChangedEventResponse();
        typedResponse.log = log;
        typedResponse.role = (byte[]) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.previousAdminRole = (byte[]) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.newAdminRole = (byte[]) eventValues.getIndexedValues().get(2).getValue();
        return typedResponse;
    }

    public Flowable<RoleAdminChangedEventResponse> roleAdminChangedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getRoleAdminChangedEventFromLog(log));
    }

    public Flowable<RoleAdminChangedEventResponse> roleAdminChangedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ROLEADMINCHANGED_EVENT));
        return roleAdminChangedEventFlowable(filter);
    }

    public static List<RoleGrantedEventResponse> getRoleGrantedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(ROLEGRANTED_EVENT, transactionReceipt);
        ArrayList<RoleGrantedEventResponse> responses = new ArrayList<RoleGrantedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            RoleGrantedEventResponse typedResponse = new RoleGrantedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.role = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.account = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.sender = (String) eventValues.getIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static RoleGrantedEventResponse getRoleGrantedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(ROLEGRANTED_EVENT, log);
        RoleGrantedEventResponse typedResponse = new RoleGrantedEventResponse();
        typedResponse.log = log;
        typedResponse.role = (byte[]) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.account = (String) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.sender = (String) eventValues.getIndexedValues().get(2).getValue();
        return typedResponse;
    }

    public Flowable<RoleGrantedEventResponse> roleGrantedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getRoleGrantedEventFromLog(log));
    }

    public Flowable<RoleGrantedEventResponse> roleGrantedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ROLEGRANTED_EVENT));
        return roleGrantedEventFlowable(filter);
    }

    public static List<RoleRevokedEventResponse> getRoleRevokedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(ROLEREVOKED_EVENT, transactionReceipt);
        ArrayList<RoleRevokedEventResponse> responses = new ArrayList<RoleRevokedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            RoleRevokedEventResponse typedResponse = new RoleRevokedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.role = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.account = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.sender = (String) eventValues.getIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static RoleRevokedEventResponse getRoleRevokedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(ROLEREVOKED_EVENT, log);
        RoleRevokedEventResponse typedResponse = new RoleRevokedEventResponse();
        typedResponse.log = log;
        typedResponse.role = (byte[]) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.account = (String) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.sender = (String) eventValues.getIndexedValues().get(2).getValue();
        return typedResponse;
    }

    public Flowable<RoleRevokedEventResponse> roleRevokedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getRoleRevokedEventFromLog(log));
    }

    public Flowable<RoleRevokedEventResponse> roleRevokedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ROLEREVOKED_EVENT));
        return roleRevokedEventFlowable(filter);
    }

    public static List<UnpausedEventResponse> getUnpausedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(UNPAUSED_EVENT, transactionReceipt);
        ArrayList<UnpausedEventResponse> responses = new ArrayList<UnpausedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            UnpausedEventResponse typedResponse = new UnpausedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.account = (String) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static UnpausedEventResponse getUnpausedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(UNPAUSED_EVENT, log);
        UnpausedEventResponse typedResponse = new UnpausedEventResponse();
        typedResponse.log = log;
        typedResponse.account = (String) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<UnpausedEventResponse> unpausedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getUnpausedEventFromLog(log));
    }

    public Flowable<UnpausedEventResponse> unpausedEventFlowable(DefaultBlockParameter startBlock,
            DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(UNPAUSED_EVENT));
        return unpausedEventFlowable(filter);
    }

    @Deprecated
    public static CommonLinkRegistry load(String contractAddress, Web3j web3j,
            Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new CommonLinkRegistry(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static CommonLinkRegistry load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new CommonLinkRegistry(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static CommonLinkRegistry load(String contractAddress, Web3j web3j,
            Credentials credentials, ContractGasProvider contractGasProvider) {
        return new CommonLinkRegistry(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static CommonLinkRegistry load(String contractAddress, Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new CommonLinkRegistry(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<CommonLinkRegistry> deploy(Web3j web3j, Credentials credentials,
            ContractGasProvider contractGasProvider, BigInteger adminDelay, String admin,
            String recorder, String pauser) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint48(adminDelay), 
                new org.web3j.abi.datatypes.Address(admin), 
                new org.web3j.abi.datatypes.Address(recorder), 
                new org.web3j.abi.datatypes.Address(pauser)));
        return deployRemoteCall(CommonLinkRegistry.class, web3j, credentials, contractGasProvider, getDeploymentBinary(), encodedConstructor);
    }

    public static RemoteCall<CommonLinkRegistry> deploy(Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider,
            BigInteger adminDelay, String admin, String recorder, String pauser) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint48(adminDelay), 
                new org.web3j.abi.datatypes.Address(admin), 
                new org.web3j.abi.datatypes.Address(recorder), 
                new org.web3j.abi.datatypes.Address(pauser)));
        return deployRemoteCall(CommonLinkRegistry.class, web3j, transactionManager, contractGasProvider, getDeploymentBinary(), encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<CommonLinkRegistry> deploy(Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit, BigInteger adminDelay, String admin,
            String recorder, String pauser) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint48(adminDelay), 
                new org.web3j.abi.datatypes.Address(admin), 
                new org.web3j.abi.datatypes.Address(recorder), 
                new org.web3j.abi.datatypes.Address(pauser)));
        return deployRemoteCall(CommonLinkRegistry.class, web3j, credentials, gasPrice, gasLimit, getDeploymentBinary(), encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<CommonLinkRegistry> deploy(Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit,
            BigInteger adminDelay, String admin, String recorder, String pauser) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint48(adminDelay), 
                new org.web3j.abi.datatypes.Address(admin), 
                new org.web3j.abi.datatypes.Address(recorder), 
                new org.web3j.abi.datatypes.Address(pauser)));
        return deployRemoteCall(CommonLinkRegistry.class, web3j, transactionManager, gasPrice, gasLimit, getDeploymentBinary(), encodedConstructor);
    }

    public static void linkLibraries(List<Contract.LinkReference> references) {
        librariesLinkedBinary = linkBinaryWithReferences(BINARY, references);
    }

    private static String getDeploymentBinary() {
        if (librariesLinkedBinary != null) {
            return librariesLinkedBinary;
        } else {
            return BINARY;
        }
    }

    protected String getStaticDeployedAddress(String networkId) {
        return _addresses.get(networkId);
    }

    public static String getPreviouslyDeployedAddress(String networkId) {
        return _addresses.get(networkId);
    }

    public static class Association extends StaticStruct {
        public byte[] sirenHash;

        public BigInteger registeredAt;

        public Association(byte[] sirenHash, BigInteger registeredAt) {
            super(new org.web3j.abi.datatypes.generated.Bytes32(sirenHash), 
                    new org.web3j.abi.datatypes.generated.Uint32(registeredAt));
            this.sirenHash = sirenHash;
            this.registeredAt = registeredAt;
        }

        public Association(Bytes32 sirenHash, Uint32 registeredAt) {
            super(sirenHash, registeredAt);
            this.sirenHash = sirenHash.getValue();
            this.registeredAt = registeredAt.getValue();
        }
    }

    public static class Campaign extends StaticStruct {
        public String association;

        public BigInteger raised;

        public BigInteger registeredAt;

        public byte[] budgetHash;

        public Campaign(String association, BigInteger raised, BigInteger registeredAt,
                byte[] budgetHash) {
            super(new org.web3j.abi.datatypes.Address(association), 
                    new org.web3j.abi.datatypes.generated.Uint96(raised), 
                    new org.web3j.abi.datatypes.generated.Uint32(registeredAt), 
                    new org.web3j.abi.datatypes.generated.Bytes32(budgetHash));
            this.association = association;
            this.raised = raised;
            this.registeredAt = registeredAt;
            this.budgetHash = budgetHash;
        }

        public Campaign(Address association, Uint96 raised, Uint32 registeredAt,
                Bytes32 budgetHash) {
            super(association, raised, registeredAt, budgetHash);
            this.association = association.getValue();
            this.raised = raised.getValue();
            this.registeredAt = registeredAt.getValue();
            this.budgetHash = budgetHash.getValue();
        }
    }

    public static class CampaignStats extends StaticStruct {
        public BigInteger totalPaidOut;

        public BigInteger payoutCount;

        public BigInteger donationCount;

        public BigInteger uniqueDonors;

        public BigInteger lastDonationAt;

        public BigInteger lastPayoutAt;

        public CampaignStats(BigInteger totalPaidOut, BigInteger payoutCount,
                BigInteger donationCount, BigInteger uniqueDonors, BigInteger lastDonationAt,
                BigInteger lastPayoutAt) {
            super(new org.web3j.abi.datatypes.generated.Uint96(totalPaidOut), 
                    new org.web3j.abi.datatypes.generated.Uint32(payoutCount), 
                    new org.web3j.abi.datatypes.generated.Uint32(donationCount), 
                    new org.web3j.abi.datatypes.generated.Uint32(uniqueDonors), 
                    new org.web3j.abi.datatypes.generated.Uint32(lastDonationAt), 
                    new org.web3j.abi.datatypes.generated.Uint32(lastPayoutAt));
            this.totalPaidOut = totalPaidOut;
            this.payoutCount = payoutCount;
            this.donationCount = donationCount;
            this.uniqueDonors = uniqueDonors;
            this.lastDonationAt = lastDonationAt;
            this.lastPayoutAt = lastPayoutAt;
        }

        public CampaignStats(Uint96 totalPaidOut, Uint32 payoutCount, Uint32 donationCount,
                Uint32 uniqueDonors, Uint32 lastDonationAt, Uint32 lastPayoutAt) {
            super(totalPaidOut, payoutCount, donationCount, uniqueDonors, lastDonationAt, lastPayoutAt);
            this.totalPaidOut = totalPaidOut.getValue();
            this.payoutCount = payoutCount.getValue();
            this.donationCount = donationCount.getValue();
            this.uniqueDonors = uniqueDonors.getValue();
            this.lastDonationAt = lastDonationAt.getValue();
            this.lastPayoutAt = lastPayoutAt.getValue();
        }
    }

    public static class Donation extends StaticStruct {
        public String donor;

        public BigInteger amount;

        public byte[] campaignId;

        public BigInteger timestamp;

        public byte[] receiptHash;

        public byte[] txRef;

        public Donation(String donor, BigInteger amount, byte[] campaignId, BigInteger timestamp,
                byte[] receiptHash, byte[] txRef) {
            super(new org.web3j.abi.datatypes.Address(donor), 
                    new org.web3j.abi.datatypes.generated.Uint96(amount), 
                    new org.web3j.abi.datatypes.generated.Bytes32(campaignId), 
                    new org.web3j.abi.datatypes.generated.Uint32(timestamp), 
                    new org.web3j.abi.datatypes.generated.Bytes32(receiptHash), 
                    new org.web3j.abi.datatypes.generated.Bytes32(txRef));
            this.donor = donor;
            this.amount = amount;
            this.campaignId = campaignId;
            this.timestamp = timestamp;
            this.receiptHash = receiptHash;
            this.txRef = txRef;
        }

        public Donation(Address donor, Uint96 amount, Bytes32 campaignId, Uint32 timestamp,
                Bytes32 receiptHash, Bytes32 txRef) {
            super(donor, amount, campaignId, timestamp, receiptHash, txRef);
            this.donor = donor.getValue();
            this.amount = amount.getValue();
            this.campaignId = campaignId.getValue();
            this.timestamp = timestamp.getValue();
            this.receiptHash = receiptHash.getValue();
            this.txRef = txRef.getValue();
        }
    }

    public static class DonorStats extends StaticStruct {
        public BigInteger totalDonated;

        public BigInteger donationCount;

        public BigInteger uniqueCampaigns;

        public BigInteger firstDonationAt;

        public DonorStats(BigInteger totalDonated, BigInteger donationCount,
                BigInteger uniqueCampaigns, BigInteger firstDonationAt) {
            super(new org.web3j.abi.datatypes.generated.Uint128(totalDonated), 
                    new org.web3j.abi.datatypes.generated.Uint32(donationCount), 
                    new org.web3j.abi.datatypes.generated.Uint32(uniqueCampaigns), 
                    new org.web3j.abi.datatypes.generated.Uint32(firstDonationAt));
            this.totalDonated = totalDonated;
            this.donationCount = donationCount;
            this.uniqueCampaigns = uniqueCampaigns;
            this.firstDonationAt = firstDonationAt;
        }

        public DonorStats(Uint128 totalDonated, Uint32 donationCount, Uint32 uniqueCampaigns,
                Uint32 firstDonationAt) {
            super(totalDonated, donationCount, uniqueCampaigns, firstDonationAt);
            this.totalDonated = totalDonated.getValue();
            this.donationCount = donationCount.getValue();
            this.uniqueCampaigns = uniqueCampaigns.getValue();
            this.firstDonationAt = firstDonationAt.getValue();
        }
    }

    public static class Payout extends StaticStruct {
        public byte[] campaignId;

        public BigInteger amount;

        public BigInteger timestamp;

        public byte[] txRef;

        public Payout(byte[] campaignId, BigInteger amount, BigInteger timestamp, byte[] txRef) {
            super(new org.web3j.abi.datatypes.generated.Bytes32(campaignId), 
                    new org.web3j.abi.datatypes.generated.Uint96(amount), 
                    new org.web3j.abi.datatypes.generated.Uint32(timestamp), 
                    new org.web3j.abi.datatypes.generated.Bytes32(txRef));
            this.campaignId = campaignId;
            this.amount = amount;
            this.timestamp = timestamp;
            this.txRef = txRef;
        }

        public Payout(Bytes32 campaignId, Uint96 amount, Uint32 timestamp, Bytes32 txRef) {
            super(campaignId, amount, timestamp, txRef);
            this.campaignId = campaignId.getValue();
            this.amount = amount.getValue();
            this.timestamp = timestamp.getValue();
            this.txRef = txRef.getValue();
        }
    }

    public static class AssociationRegisteredEventResponse extends BaseEventResponse {
        public String association;

        public byte[] sirenHash;

        public BigInteger registeredAt;
    }

    public static class CampaignBudgetUpdatedEventResponse extends BaseEventResponse {
        public byte[] campaignId;

        public byte[] oldBudgetHash;

        public byte[] newBudgetHash;

        public BigInteger timestamp;
    }

    public static class CampaignRegisteredEventResponse extends BaseEventResponse {
        public byte[] campaignId;

        public String association;

        public byte[] budgetHash;

        public BigInteger registeredAt;
    }

    public static class DefaultAdminDelayChangeCanceledEventResponse extends BaseEventResponse {
    }

    public static class DefaultAdminDelayChangeScheduledEventResponse extends BaseEventResponse {
        public BigInteger newDelay;

        public BigInteger effectSchedule;
    }

    public static class DefaultAdminTransferCanceledEventResponse extends BaseEventResponse {
    }

    public static class DefaultAdminTransferScheduledEventResponse extends BaseEventResponse {
        public String newAdmin;

        public BigInteger acceptSchedule;
    }

    public static class DonationRecordedEventResponse extends BaseEventResponse {
        public String donor;

        public byte[] campaignId;

        public String association;

        public byte[] donationId;

        public BigInteger amount;

        public byte[] receiptHash;

        public byte[] txRef;
    }

    public static class DonorFirstDonationEventResponse extends BaseEventResponse {
        public String donor;

        public BigInteger timestamp;
    }

    public static class DonorJoinedCampaignEventResponse extends BaseEventResponse {
        public String donor;

        public byte[] campaignId;
    }

    public static class PausedEventResponse extends BaseEventResponse {
        public String account;
    }

    public static class PayoutRecordedEventResponse extends BaseEventResponse {
        public byte[] payoutId;

        public byte[] campaignId;

        public BigInteger amount;

        public byte[] txRef;

        public BigInteger timestamp;
    }

    public static class RoleAdminChangedEventResponse extends BaseEventResponse {
        public byte[] role;

        public byte[] previousAdminRole;

        public byte[] newAdminRole;
    }

    public static class RoleGrantedEventResponse extends BaseEventResponse {
        public byte[] role;

        public String account;

        public String sender;
    }

    public static class RoleRevokedEventResponse extends BaseEventResponse {
        public byte[] role;

        public String account;

        public String sender;
    }

    public static class UnpausedEventResponse extends BaseEventResponse {
        public String account;
    }
}
