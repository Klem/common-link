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
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint32;
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
import org.web3j.tuples.generated.Tuple4;
import org.web3j.tuples.generated.Tuple6;
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
    public static final String BINARY = "0x608060405234801561000f575f5ffd5b50604051611f3b380380611f3b83398101604081905261002e9161018a565b60017f9b779b17422d0df92223018b32b4d1fa46e071723d6817e2486d003becc55f00556001600160a01b03821661007957604051630b5eba9f60e41b815260040160405180910390fd5b6100835f836100c6565b506001600160a01b038116156100bf576100bd7ff996da754c790e95d5c7ca3330cfcad529487fe9d1d8edb7afc65076fdf9adb4826100c6565b505b50506101bb565b5f828152602081815260408083206001600160a01b038516845290915281205460ff16610166575f838152602081815260408083206001600160a01b03861684529091529020805460ff1916600117905561011e3390565b6001600160a01b0316826001600160a01b0316847f2f8788117e7eff1d82e926ec794901d17c78024a50270940304540a733656f0d60405160405180910390a4506001610169565b505f5b92915050565b80516001600160a01b0381168114610185575f5ffd5b919050565b5f5f6040838503121561019b575f5ffd5b6101a48361016f565b91506101b26020840161016f565b90509250929050565b611d73806101c85f395ff3fe608060405234801561000f575f5ffd5b50600436106101e7575f3560e01c80637354efa7116101095780639fed4fbc1161009e578063c8eb0c881161006e578063c8eb0c88146107b7578063cabe0452146108a1578063d547741f1461097c578063ffa1ad741461098f575f5ffd5b80639fed4fbc146106e8578063a217fddf146106fb578063bea8658114610702578063bed866f614610716575f5ffd5b806391d14854116100d957806391d148541461058957806392ce70ed1461059c57806395d1d4c5146106165780639eca2f1e1461065c575f5ffd5b80637354efa71461051d5780638456cb591461053057806384e50a1c146105385780638d6d492514610576575f5ffd5b806336568abe1161017f57806347093c741161014f57806347093c74146104ca57806347d212da146104ec57806352ab4bc0146104ff5780635c975abb14610512575f5ffd5b806336568abe146103535780633f4ba83a14610366578063449ffb931461036e578063454cd16a146103b9575f5ffd5b806318c4b991116101ba57806318c4b9911461027b578063248a9ca3146102fd57806327bc23f01461032d5780632f2ff15d14610340575f5ffd5b806301ffc9a7146101eb578063057110d7146102135780630f49699e1461025357806313172b5814610268575b5f5ffd5b6101fe6101f9366004611a6d565b6109c4565b60405190151581526020015b60405180910390f35b61023b610221366004611a9b565b60036020525f90815260409020546001600160a01b031681565b6040516001600160a01b03909116815260200161020a565b610266610261366004611acd565b6109fa565b005b610266610276366004611b00565b610baa565b6102c7610289366004611b20565b60066020525f90815260409020546001600160801b0381169063ffffffff600160801b8204811691600160a01b8104821691600160c01b9091041684565b604080516001600160801b03909516855263ffffffff93841660208601529183169184019190915216606082015260800161020a565b61031f61030b366004611a9b565b5f9081526020819052604090206001015490565b60405190815260200161020a565b61026661033b366004611b39565b610c8d565b61026661034e366004611b61565b610e29565b610266610361366004611b61565b610e53565b610266610e8b565b6103a161037c366004611b39565b600760209081525f92835260408084209091529082529020546001600160801b031681565b6040516001600160801b03909116815260200161020a565b61046b6103c7366004611a9b565b6040805160c0810182525f80825260208201819052918101829052606081018290526080810182905260a0810191909152505f90815260056020908152604091829020825160c08101845281546001600160a01b0381168252600160a01b90046001600160601b031692810192909252600181015492820192909252600282015463ffffffff1660608201526003820154608082015260049091015460a082015290565b60405161020a919081516001600160a01b031681526020808301516001600160601b0316908201526040808301519082015260608083015163ffffffff16908201526080808301519082015260a0918201519181019190915260c00190565b6101fe6104d8366004611a9b565b60086020525f908152604090205460ff1681565b6102666104fa366004611b20565b610ea0565b61031f61050d366004611b8b565b610f6a565b60015460ff166101fe565b61026661052b366004611bdc565b6110e2565b610266611671565b6103a1610546366004611b39565b6001600160a01b03919091165f90815260076020908152604080832093835292905220546001600160801b031690565b60095461023b906001600160a01b031681565b6101fe610597366004611b61565b611683565b6105f56105aa366004611b20565b604080518082019091525f8082526020820152506001600160a01b03165f908152600260209081526040918290208251808401909352805483526001015463ffffffff169082015290565b604080518251815260209283015163ffffffff16928101929092520161020a565b610642610624366004611b20565b60026020525f90815260409020805460019091015463ffffffff1682565b6040805192835263ffffffff90911660208301520161020a565b6106ab61066a366004611a9b565b60046020525f90815260409020805460018201546002909201546001600160a01b03821692600160a01b9092046001600160601b03169163ffffffff169084565b604080516001600160a01b0390951685526001600160601b03909316602085015263ffffffff90911691830191909152606082015260800161020a565b6102666106f6366004611c30565b6116ab565b61031f5f81565b61031f5f516020611cfe5f395f51905f5281565b610772610724366004611a9b565b60056020525f9081526040902080546001820154600283015460038401546004909401546001600160a01b03841694600160a01b9094046001600160601b03169363ffffffff909216919086565b604080516001600160a01b0390971687526001600160601b0390951660208701529385019290925263ffffffff166060840152608083015260a082015260c00161020a565b61084f6107c5366004611b20565b604080516080810182525f808252602082018190529181018290526060810191909152506001600160a01b03165f90815260066020908152604091829020825160808101845290546001600160801b038116825263ffffffff600160801b8204811693830193909352600160a01b8104831693820193909352600160c01b90920416606082015290565b60405161020a91905f6080820190506001600160801b03835116825263ffffffff602084015116602083015263ffffffff604084015116604083015263ffffffff606084015116606083015292915050565b6109316108af366004611a9b565b604080516080810182525f808252602082018190529181018290526060810191909152505f90815260046020908152604091829020825160808101845281546001600160a01b0381168252600160a01b90046001600160601b031692810192909252600181015463ffffffff1692820192909252600290910154606082015290565b60405161020a919081516001600160a01b031681526020808301516001600160601b03169082015260408083015163ffffffff16908201526060918201519181019190915260800190565b61026661098a366004611b61565b6117ea565b6109b7604051806040016040528060098152602001680322e302e302d6d76760bc1b81525081565b60405161020a9190611c62565b5f6001600160e01b03198216637965db0b60e01b14806109f457506301ffc9a760e01b6001600160e01b03198316145b92915050565b610a0261180e565b5f516020611cfe5f395f51905f52610a1981611834565b83610a3757604051633925755160e11b815260040160405180910390fd5b5f8481526004602052604090205484906001600160a01b031615610a7a5760405163d9c9ecb960e01b8152600401610a7191815260200190565b60405180910390fd5b506001600160a01b0383165f90815260026020526040902060010154839063ffffffff16610ac75760405163cfaae3e360e01b81526001600160a01b039091166004820152602401610a71565b5081610ae657604051635b5c5f7960e11b815260040160405180910390fd5b604080516080810182526001600160a01b038581168083525f602080850182815263ffffffff42818116888a0181815260608a018d81528f885260048752968b9020995194516001600160601b0316600160a01b029490981693909317885595516001880180549190921663ffffffff1990911617905591516002909501949094558451878152938401529092909187917f62ca68d211749e65bb7a0e4f603de589fabd4bf1fd9ad011783bc0c2bee3cbe591015b60405180910390a35050505050565b610bb261180e565b5f516020611cfe5f395f51905f52610bc981611834565b5f83815260046020526040902054839081906001600160a01b0316610c045760405163faed87b960e01b8152600401610a7191815260200190565b5082610c2357604051635b5c5f7960e11b815260040160405180910390fd5b5f848152600460209081526040918290206002810180549087905583518181529283018790524263ffffffff1683850152925190929187917fa84ca322cb316b896ef1c17b7e553c19db5ae4b930536ceb7c27e5bdc4caa6d09181900360600190a2505050505050565b610c9561180e565b5f516020611cfe5f395f51905f52610cac81611834565b6001600160a01b038316610cd35760405163456db08160e01b815260040160405180910390fd5b81610cf1576040516334c1f42f60e21b815260040160405180910390fd5b6001600160a01b0383165f90815260026020526040902060010154839063ffffffff1615610d3e57604051637481f26b60e11b81526001600160a01b039091166004820152602401610a71565b505f828152600360205260409020546001600160a01b031682818015610d89576040516326b3def960e01b815260048101929092526001600160a01b03166024820152604401610a71565b50506040805180820182528481524263ffffffff81811660208085018281526001600160a01b038b165f818152600284528881209751885591516001909701805463ffffffff19169790951696909617909355888352600381529185902080546001600160a01b03191685179055935193845290928692917f885c7b58a8f0dc64e63ef94e5f621b87d3638a24d073ba1da87f242f30e391a59101610b9b565b5f82815260208190526040902060010154610e4381611834565b610e4d838361183e565b50505050565b6001600160a01b0381163314610e7c5760405163334bd91960e11b815260040160405180910390fd5b610e8682826118cd565b505050565b5f610e9581611834565b610e9d611936565b50565b5f610eaa81611834565b6001600160a01b03821615801590610ecb57506001600160a01b0382163b15155b610ee857604051631f7dcb6760e11b815260040160405180910390fd5b6009546001600160a01b03168015610f1f57604051630d6b043160e01b81526001600160a01b039091166004820152602401610a71565b50600980546001600160a01b0319166001600160a01b0384169081179091556040517febcf097c36b55ec4f09f32f44dba2f7d0991bc9cd161c86bca6d4ba726c7be8e905f90a25050565b5f610f7361180e565b610f7b611988565b5f516020611cfe5f395f51905f52610f9281611834565b6009546001600160a01b031680610fbc57604051631929e65d60e11b815260040160405180910390fd5b6001600160a01b0385165f908152600660205260409020548590600160c01b900463ffffffff1661100c5760405163ccc99fad60e01b81526001600160a01b039091166004820152602401610a71565b5060405163ad0be4bd60e01b81526001600160a01b03868116600483015261ffff8616602483015282169063ad0be4bd906044016020604051808303815f875af115801561105c573d5f5f3e3d5ffd5b505050506040513d601f19601f820116820180604052508101906110809190611c97565b92508361ffff16856001600160a01b03167faaeb345bcadf01b040a2702427b97a71f24a3696ca416dc9196af0a57a4c922c856040516110c291815260200190565b60405180910390a350506109f460015f516020611d1e5f395f51905f5255565b6110ea61180e565b6110f2611988565b5f516020611cfe5f395f51905f5261110981611834565b5f85815260046020526040902054859081906001600160a01b03166111445760405163faed87b960e01b8152600401610a7191815260200190565b50876111635760405163bc09d34760e01b815260040160405180910390fd5b5f8881526005602052604090205488906001600160a01b03161561119d576040516311576e8f60e11b8152600401610a7191815260200190565b506001600160a01b0387166111c3576040516148b160f01b815260040160405180910390fd5b846001600160601b03165f036111ec5760405163162908e360e11b815260040160405180910390fd5b8361120a5760405163b4479ffd60e01b815260040160405180910390fd5b8261122857604051639ca53c0d60e01b815260040160405180910390fd5b5f60045f8881526020019081526020015f2090506040518060c00160405280896001600160a01b03168152602001876001600160601b031681526020018881526020014263ffffffff1681526020018681526020018581525060055f8b81526020019081526020015f205f820151815f015f6101000a8154816001600160a01b0302191690836001600160a01b031602179055506020820151815f0160146101000a8154816001600160601b0302191690836001600160601b03160217905550604082015181600101556060820151816002015f6101000a81548163ffffffff021916908363ffffffff1602179055506080820151816003015560a082015181600401559050505f815f0160149054906101000a90046001600160601b031690505f8782019050816001600160601b0316816001600160601b0316101589838a9091926113a2576040516390c9dc8d60e01b815260048101939093526001600160601b039182166024840152166044820152606401610a71565b505083546001600160601b03909216600160a01b026001600160a01b039283161784555089165f9081526006602090815260408083208054600784528285208d865290935292205491925063ffffffff600160c01b9091041615906001600160801b031615811561142a57825463ffffffff60c01b1916600160c01b4263ffffffff16021783555b82546001600160601b038a169084905f9061144f9084906001600160801b0316611cc2565b92506101000a8154816001600160801b0302191690836001600160801b031602179055506001835f0160108282829054906101000a900463ffffffff166114969190611ce1565b92506101000a81548163ffffffff021916908363ffffffff16021790555080156114fb57825460019084906014906114dc908490600160a01b900463ffffffff16611ce1565b92506101000a81548163ffffffff021916908363ffffffff1602179055505b6001600160a01b038b165f9081526007602090815260408083208d8452909152812080546001600160601b038c16929061153f9084906001600160801b0316611cc2565b82546001600160801b039182166101009390930a9283029190920219909116179055508354604080518e81526001600160601b038c1660208201529081018a9052606081018990526001600160a01b03918216918c91908e16907fb32069e9fcbfc4a80516228c2e119a46b052b381de75b7386123658b1e76d9759060800160405180910390a481156116115760405163ffffffff421681526001600160a01b038c16907f968352eaec1cf3006fda21fe37c5c004429add6106ff33412a88cf776ed9d5b89060200160405180910390a25b801561164d576040518a906001600160a01b038d16907ffeb17b547cef785d92dec6b967366250be5f333e4d4a0cd7d83e69d9a897337e905f90a35b50505050505061166960015f516020611d1e5f395f51905f5255565b505050505050565b5f61167b81611834565b610e9d6119a3565b5f918252602082815260408084206001600160a01b0393909316845291905290205460ff1690565b6116b361180e565b5f516020611cfe5f395f51905f526116ca81611834565b5f83815260046020526040902054839081906001600160a01b03166117055760405163faed87b960e01b8152600401610a7191815260200190565b50846117245760405163678dc0c360e01b815260040160405180910390fd5b5f85815260086020526040902054859060ff161561175857604051630a4de49b60e11b8152600401610a7191815260200190565b50826001600160601b03165f036117825760405163162908e360e11b815260040160405180910390fd5b5f8581526008602052604090819020805460ff1916600117905551849086907fcbe496fe20dce4de56156f13fc2c4dfb97a2e2dc0846708572ae360d77e892a690610b9b90879042906001600160601b0392909216825263ffffffff16602082015260400190565b5f8281526020819052604090206001015461180481611834565b610e4d83836118cd565b60015460ff16156118325760405163d93c066560e01b815260040160405180910390fd5b565b610e9d81336119de565b5f6118498383611683565b6118c6575f838152602081815260408083206001600160a01b03861684529091529020805460ff1916600117905561187e3390565b6001600160a01b0316826001600160a01b0316847f2f8788117e7eff1d82e926ec794901d17c78024a50270940304540a733656f0d60405160405180910390a45060016109f4565b505f6109f4565b5f6118d88383611683565b156118c6575f838152602081815260408083206001600160a01b0386168085529252808320805460ff1916905551339286917ff6391f5c32d9c69d2a47ea670b442974b53935d1edc7fd64eb21e047a839171b9190a45060016109f4565b61193e611a1b565b6001805460ff191690557f5db9ee0a495bf2e6ff9c91a7834c1ba4fdd244a5e8aa4e537bd38aeae4b073aa335b6040516001600160a01b03909116815260200160405180910390a1565b611990611a3e565b60025f516020611d1e5f395f51905f5255565b6119ab61180e565b6001805460ff1916811790557f62e78cea01bee320cd4e420270b5ea74000d11b0c9f74754ebdbfc544b05a2583361196b565b6119e88282611683565b611a175760405163e2517d3f60e01b81526001600160a01b038216600482015260248101839052604401610a71565b5050565b60015460ff1661183257604051638dfc202b60e01b815260040160405180910390fd5b5f516020611d1e5f395f51905f525460020361183257604051633ee5aeb560e01b815260040160405180910390fd5b5f60208284031215611a7d575f5ffd5b81356001600160e01b031981168114611a94575f5ffd5b9392505050565b5f60208284031215611aab575f5ffd5b5035919050565b80356001600160a01b0381168114611ac8575f5ffd5b919050565b5f5f5f60608486031215611adf575f5ffd5b83359250611aef60208501611ab2565b929592945050506040919091013590565b5f5f60408385031215611b11575f5ffd5b50508035926020909101359150565b5f60208284031215611b30575f5ffd5b611a9482611ab2565b5f5f60408385031215611b4a575f5ffd5b611b5383611ab2565b946020939093013593505050565b5f5f60408385031215611b72575f5ffd5b82359150611b8260208401611ab2565b90509250929050565b5f5f60408385031215611b9c575f5ffd5b611ba583611ab2565b9150602083013561ffff81168114611bbb575f5ffd5b809150509250929050565b80356001600160601b0381168114611ac8575f5ffd5b5f5f5f5f5f5f60c08789031215611bf1575f5ffd5b86359550611c0160208801611ab2565b945060408701359350611c1660608801611bc6565b9598949750929560808101359460a0909101359350915050565b5f5f5f60608486031215611c42575f5ffd5b8335925060208401359150611c5960408501611bc6565b90509250925092565b602081525f82518060208401528060208501604085015e5f604082850101526040601f19601f83011684010191505092915050565b5f60208284031215611ca7575f5ffd5b5051919050565b634e487b7160e01b5f52601160045260245ffd5b6001600160801b0381811683821601908111156109f4576109f4611cae565b63ffffffff81811683821601908111156109f4576109f4611cae56fef996da754c790e95d5c7ca3330cfcad529487fe9d1d8edb7afc65076fdf9adb49b779b17422d0df92223018b32b4d1fa46e071723d6817e2486d003becc55f00a2646970667358221220dc73f45e1002369eb1e442d4526dd9ee48581969f8c24088c9b216cb6dd2fa1964736f6c634300081e0033";

    private static String librariesLinkedBinary;

    public static final String FUNC_DEFAULT_ADMIN_ROLE = "DEFAULT_ADMIN_ROLE";

    public static final String FUNC_RECORDER_ROLE = "RECORDER_ROLE";

    public static final String FUNC_VERSION = "VERSION";

    public static final String FUNC_ASSOCIATIONS = "associations";

    public static final String FUNC_BADGECONTRACT = "badgeContract";

    public static final String FUNC_CAMPAIGNS = "campaigns";

    public static final String FUNC_DONATIONS = "donations";

    public static final String FUNC_DONORCAMPAIGNTOTAL = "donorCampaignTotal";

    public static final String FUNC_DONORSTATS = "donorStats";

    public static final String FUNC_GETASSOCIATION = "getAssociation";

    public static final String FUNC_GETCAMPAIGN = "getCampaign";

    public static final String FUNC_GETDONATION = "getDonation";

    public static final String FUNC_GETDONORCAMPAIGNTOTAL = "getDonorCampaignTotal";

    public static final String FUNC_GETDONORSTATS = "getDonorStats";

    public static final String FUNC_GETROLEADMIN = "getRoleAdmin";

    public static final String FUNC_GRANTROLE = "grantRole";

    public static final String FUNC_HASROLE = "hasRole";

    public static final String FUNC_MINTBADGE = "mintBadge";

    public static final String FUNC_PAUSE = "pause";

    public static final String FUNC_PAUSED = "paused";

    public static final String FUNC_PAYOUTRECORDED = "payoutRecorded";

    public static final String FUNC_RECORDDONATION = "recordDonation";

    public static final String FUNC_RECORDPAYOUT = "recordPayout";

    public static final String FUNC_REGISTERASSOCIATION = "registerAssociation";

    public static final String FUNC_REGISTERCAMPAIGN = "registerCampaign";

    public static final String FUNC_RENOUNCEROLE = "renounceRole";

    public static final String FUNC_REVOKEROLE = "revokeRole";

    public static final String FUNC_SETBADGECONTRACT = "setBadgeContract";

    public static final String FUNC_SIRENHASHTOASSOCIATION = "sirenHashToAssociation";

    public static final String FUNC_SUPPORTSINTERFACE = "supportsInterface";

    public static final String FUNC_UNPAUSE = "unpause";

    public static final String FUNC_UPDATECAMPAIGNBUDGET = "updateCampaignBudget";

    public static final Event ASSOCIATIONREGISTERED_EVENT = new Event("AssociationRegistered", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Bytes32>(true) {}, new TypeReference<Uint32>() {}));
    ;

    public static final Event BADGECONTRACTSET_EVENT = new Event("BadgeContractSet", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}));
    ;

    public static final Event BADGEMINTED_EVENT = new Event("BadgeMinted", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint16>(true) {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event CAMPAIGNBUDGETUPDATED_EVENT = new Event("CampaignBudgetUpdated", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>(true) {}, new TypeReference<Bytes32>() {}, new TypeReference<Bytes32>() {}, new TypeReference<Uint32>() {}));
    ;

    public static final Event CAMPAIGNREGISTERED_EVENT = new Event("CampaignRegistered", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Bytes32>() {}, new TypeReference<Uint32>() {}));
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
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>(true) {}, new TypeReference<Bytes32>(true) {}, new TypeReference<Uint96>() {}, new TypeReference<Uint32>() {}));
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

    public RemoteFunctionCall<Tuple2<byte[], BigInteger>> associations(String param0) {
        final Function function = new Function(FUNC_ASSOCIATIONS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}, new TypeReference<Uint32>() {}));
        return new RemoteFunctionCall<Tuple2<byte[], BigInteger>>(function,
                new Callable<Tuple2<byte[], BigInteger>>() {
                    @Override
                    public Tuple2<byte[], BigInteger> call() throws Exception {
                        List<Type> results = executeCallMultipleValueReturn(function);
                        return new Tuple2<byte[], BigInteger>(
                                (byte[]) results.get(0).getValue(), 
                                (BigInteger) results.get(1).getValue());
                    }
                });
    }

    public RemoteFunctionCall<String> badgeContract() {
        final Function function = new Function(FUNC_BADGECONTRACT, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<Tuple4<String, BigInteger, BigInteger, byte[]>> campaigns(
            byte[] param0) {
        final Function function = new Function(FUNC_CAMPAIGNS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Uint96>() {}, new TypeReference<Uint32>() {}, new TypeReference<Bytes32>() {}));
        return new RemoteFunctionCall<Tuple4<String, BigInteger, BigInteger, byte[]>>(function,
                new Callable<Tuple4<String, BigInteger, BigInteger, byte[]>>() {
                    @Override
                    public Tuple4<String, BigInteger, BigInteger, byte[]> call() throws Exception {
                        List<Type> results = executeCallMultipleValueReturn(function);
                        return new Tuple4<String, BigInteger, BigInteger, byte[]>(
                                (String) results.get(0).getValue(), 
                                (BigInteger) results.get(1).getValue(), 
                                (BigInteger) results.get(2).getValue(), 
                                (byte[]) results.get(3).getValue());
                    }
                });
    }

    public RemoteFunctionCall<Tuple6<String, BigInteger, byte[], BigInteger, byte[], byte[]>> donations(
            byte[] param0) {
        final Function function = new Function(FUNC_DONATIONS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Uint96>() {}, new TypeReference<Bytes32>() {}, new TypeReference<Uint32>() {}, new TypeReference<Bytes32>() {}, new TypeReference<Bytes32>() {}));
        return new RemoteFunctionCall<Tuple6<String, BigInteger, byte[], BigInteger, byte[], byte[]>>(function,
                new Callable<Tuple6<String, BigInteger, byte[], BigInteger, byte[], byte[]>>() {
                    @Override
                    public Tuple6<String, BigInteger, byte[], BigInteger, byte[], byte[]> call()
                            throws Exception {
                        List<Type> results = executeCallMultipleValueReturn(function);
                        return new Tuple6<String, BigInteger, byte[], BigInteger, byte[], byte[]>(
                                (String) results.get(0).getValue(), 
                                (BigInteger) results.get(1).getValue(), 
                                (byte[]) results.get(2).getValue(), 
                                (BigInteger) results.get(3).getValue(), 
                                (byte[]) results.get(4).getValue(), 
                                (byte[]) results.get(5).getValue());
                    }
                });
    }

    public RemoteFunctionCall<BigInteger> donorCampaignTotal(String param0, byte[] param1) {
        final Function function = new Function(FUNC_DONORCAMPAIGNTOTAL, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(param0), 
                new org.web3j.abi.datatypes.generated.Bytes32(param1)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint128>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<Tuple4<BigInteger, BigInteger, BigInteger, BigInteger>> donorStats(
            String param0) {
        final Function function = new Function(FUNC_DONORSTATS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint128>() {}, new TypeReference<Uint32>() {}, new TypeReference<Uint32>() {}, new TypeReference<Uint32>() {}));
        return new RemoteFunctionCall<Tuple4<BigInteger, BigInteger, BigInteger, BigInteger>>(function,
                new Callable<Tuple4<BigInteger, BigInteger, BigInteger, BigInteger>>() {
                    @Override
                    public Tuple4<BigInteger, BigInteger, BigInteger, BigInteger> call() throws
                            Exception {
                        List<Type> results = executeCallMultipleValueReturn(function);
                        return new Tuple4<BigInteger, BigInteger, BigInteger, BigInteger>(
                                (BigInteger) results.get(0).getValue(), 
                                (BigInteger) results.get(1).getValue(), 
                                (BigInteger) results.get(2).getValue(), 
                                (BigInteger) results.get(3).getValue());
                    }
                });
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

    public RemoteFunctionCall<TransactionReceipt> mintBadge(String donor, BigInteger badgeType) {
        final Function function = new Function(
                FUNC_MINTBADGE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(donor), 
                new org.web3j.abi.datatypes.generated.Uint16(badgeType)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
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

    public RemoteFunctionCall<Boolean> payoutRecorded(byte[] param0) {
        final Function function = new Function(FUNC_PAYOUTRECORDED, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
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
            BigInteger amountCents) {
        final Function function = new Function(
                FUNC_RECORDPAYOUT, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(payoutId), 
                new org.web3j.abi.datatypes.generated.Bytes32(campaignId), 
                new org.web3j.abi.datatypes.generated.Uint96(amountCents)), 
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

    public RemoteFunctionCall<TransactionReceipt> renounceRole(byte[] role,
            String callerConfirmation) {
        final Function function = new Function(
                FUNC_RENOUNCEROLE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(role), 
                new org.web3j.abi.datatypes.Address(callerConfirmation)), 
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

    public RemoteFunctionCall<TransactionReceipt> setBadgeContract(String badge) {
        final Function function = new Function(
                FUNC_SETBADGECONTRACT, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(badge)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<String> sirenHashToAssociation(byte[] param0) {
        final Function function = new Function(FUNC_SIRENHASHTOASSOCIATION, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
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

    public static List<BadgeContractSetEventResponse> getBadgeContractSetEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(BADGECONTRACTSET_EVENT, transactionReceipt);
        ArrayList<BadgeContractSetEventResponse> responses = new ArrayList<BadgeContractSetEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            BadgeContractSetEventResponse typedResponse = new BadgeContractSetEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.badgeContract = (String) eventValues.getIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static BadgeContractSetEventResponse getBadgeContractSetEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(BADGECONTRACTSET_EVENT, log);
        BadgeContractSetEventResponse typedResponse = new BadgeContractSetEventResponse();
        typedResponse.log = log;
        typedResponse.badgeContract = (String) eventValues.getIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<BadgeContractSetEventResponse> badgeContractSetEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getBadgeContractSetEventFromLog(log));
    }

    public Flowable<BadgeContractSetEventResponse> badgeContractSetEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(BADGECONTRACTSET_EVENT));
        return badgeContractSetEventFlowable(filter);
    }

    public static List<BadgeMintedEventResponse> getBadgeMintedEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = staticExtractEventParametersWithLog(BADGEMINTED_EVENT, transactionReceipt);
        ArrayList<BadgeMintedEventResponse> responses = new ArrayList<BadgeMintedEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            BadgeMintedEventResponse typedResponse = new BadgeMintedEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.donor = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.badgeType = (BigInteger) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.tokenId = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public static BadgeMintedEventResponse getBadgeMintedEventFromLog(Log log) {
        Contract.EventValuesWithLog eventValues = staticExtractEventParametersWithLog(BADGEMINTED_EVENT, log);
        BadgeMintedEventResponse typedResponse = new BadgeMintedEventResponse();
        typedResponse.log = log;
        typedResponse.donor = (String) eventValues.getIndexedValues().get(0).getValue();
        typedResponse.badgeType = (BigInteger) eventValues.getIndexedValues().get(1).getValue();
        typedResponse.tokenId = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
        return typedResponse;
    }

    public Flowable<BadgeMintedEventResponse> badgeMintedEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(log -> getBadgeMintedEventFromLog(log));
    }

    public Flowable<BadgeMintedEventResponse> badgeMintedEventFlowable(
            DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(BADGEMINTED_EVENT));
        return badgeMintedEventFlowable(filter);
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
            typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
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
        typedResponse.timestamp = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
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
            ContractGasProvider contractGasProvider, String admin, String recorder) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(admin), 
                new org.web3j.abi.datatypes.Address(recorder)));
        return deployRemoteCall(CommonLinkRegistry.class, web3j, credentials, contractGasProvider, getDeploymentBinary(), encodedConstructor);
    }

    public static RemoteCall<CommonLinkRegistry> deploy(Web3j web3j,
            TransactionManager transactionManager, ContractGasProvider contractGasProvider,
            String admin, String recorder) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(admin), 
                new org.web3j.abi.datatypes.Address(recorder)));
        return deployRemoteCall(CommonLinkRegistry.class, web3j, transactionManager, contractGasProvider, getDeploymentBinary(), encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<CommonLinkRegistry> deploy(Web3j web3j, Credentials credentials,
            BigInteger gasPrice, BigInteger gasLimit, String admin, String recorder) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(admin), 
                new org.web3j.abi.datatypes.Address(recorder)));
        return deployRemoteCall(CommonLinkRegistry.class, web3j, credentials, gasPrice, gasLimit, getDeploymentBinary(), encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<CommonLinkRegistry> deploy(Web3j web3j,
            TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit,
            String admin, String recorder) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(admin), 
                new org.web3j.abi.datatypes.Address(recorder)));
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

    public static class AssociationRegisteredEventResponse extends BaseEventResponse {
        public String association;

        public byte[] sirenHash;

        public BigInteger registeredAt;
    }

    public static class BadgeContractSetEventResponse extends BaseEventResponse {
        public String badgeContract;
    }

    public static class BadgeMintedEventResponse extends BaseEventResponse {
        public String donor;

        public BigInteger badgeType;

        public BigInteger tokenId;
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
