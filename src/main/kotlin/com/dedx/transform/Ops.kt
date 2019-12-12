package com.dedx.transform

interface StackOp {
    val size: Int
}

class StackPop(override val size: Int) : StackOp
class StackPush(override val size: Int) : StackOp

enum class Ops constructor(val opcode: Int, vararg ops: StackOp) {
    NOP(0), // visitInsn
    ACONST_NULL(1, StackPush(1)), // -
    ICONST_M1(2, StackPush(1)), // -
    ICONST_0(3, StackPush(1)), // -
    ICONST_1(4, StackPush(1)), // -
    ICONST_2(5, StackPush(1)), // -
    ICONST_3(6, StackPush(1)), // -
    ICONST_4(7, StackPush(1)), // -
    ICONST_5(8, StackPush(1)), // -
    LCONST_0(9, StackPush(2)), // -
    LCONST_1(10, StackPush(2)), // -
    FCONST_0(11, StackPush(1)), // -
    FCONST_1(12, StackPush(1)), // -
    FCONST_2(13, StackPush(1)), // -
    DCONST_0(14, StackPush(2)), // -
    DCONST_1(15, StackPush(2)), // -
    BIPUSH(16, StackPush(1)), // visitIntInsn
    SIPUSH(17, StackPush(1)), // -
    LDC(18, StackPush(1)), // visitLdcInsn
    ILOAD(21, StackPush(1)), // visitVarInsn
    LLOAD(22, StackPush(2)), // -
    FLOAD(23, StackPush(1)), // -
    DLOAD(24, StackPush(2)), // -
    ALOAD(25, StackPush(1)), // -
    IALOAD(46, StackPush(1)), // visitInsn
    LALOAD(47, StackPush(1)), // -
    FALOAD(48, StackPush(1)), // -
    DALOAD(49, StackPush(1)), // -
    AALOAD(50, StackPush(1)), // -
    BALOAD(51, StackPush(1)), // -
    CALOAD(52, StackPush(1)), // -
    SALOAD(53, StackPush(1)), // -
    ISTORE(54, StackPop(1)), // visitVarInsn
    LSTORE(55, StackPop(2)), // -
    FSTORE(56, StackPop(1)), // -
    DSTORE(57, StackPop(2)), // -
    ASTORE(58, StackPop(1)), // -
    IASTORE(79, StackPop(1)), // visitInsn
    LASTORE(80, StackPop(1)), // -
    FASTORE(81, StackPop(1)), // -
    DASTORE(82, StackPop(1)), // -
    AASTORE(83, StackPop(1)), // -
    BASTORE(84, StackPop(1)), // -
    CASTORE(85, StackPop(1)), // -
    SASTORE(86, StackPop(1)), // -
    POP(87, StackPop(1)), // -
    POP2(88, StackPop(2)), // -
    DUP(89, StackPush(1)), // -
    DUP_X1(90), // -
    DUP_X2(91), // -
    DUP2(92, StackPush(2)), // -
    DUP2_X1(93), // -
    DUP2_X2(94), // -
    SWAP(95), // -
    IADD(96, StackPop(2), StackPush(1)), // -
    LADD(97, StackPop(4), StackPush(2)), // -
    FADD(98, StackPop(2), StackPush(1)), // -
    DADD(99, StackPop(4), StackPush(2)), // -
    ISUB(100, StackPop(2), StackPush(1)), // -
    LSUB(101, StackPop(4), StackPush(2)), // -
    FSUB(102, StackPop(2), StackPush(1)), // -
    DSUB(103, StackPop(4), StackPush(2)), // -
    IMUL(104, StackPop(2), StackPush(1)), // -
    LMUL(105, StackPop(4), StackPush(2)), // -
    FMUL(106, StackPop(2), StackPush(1)), // -
    DMUL(107, StackPop(4), StackPush(2)), // -
    IDIV(108, StackPop(2), StackPush(1)), // -
    LDIV(109, StackPop(4), StackPush(2)), // -
    FDIV(110, StackPop(2), StackPush(1)), // -
    DDIV(111, StackPop(4), StackPush(2)), // -
    IREM(112, StackPop(2), StackPush(1)), // -
    LREM(113, StackPop(4), StackPush(2)), // -
    FREM(114, StackPop(2), StackPush(1)), // -
    DREM(115, StackPop(4), StackPush(2)), // -
    INEG(116), // -
    LNEG(117), // -
    FNEG(118), // -
    DNEG(119), // -
    ISHL(120, StackPop(2), StackPush(1)), // -
    LSHL(121, StackPop(4), StackPush(2)), // -
    ISHR(122, StackPop(2), StackPush(1)), // -
    LSHR(123, StackPop(4), StackPush(2)), // -
    IUSHR(124, StackPop(2), StackPush(1)), // -
    LUSHR(125, StackPop(4), StackPush(2)), // -
    IAND(126, StackPop(2), StackPush(1)), // -
    LAND(127, StackPop(4), StackPush(2)), // -
    IOR(128, StackPop(2), StackPush(1)), // -
    LOR(129, StackPop(4), StackPush(2)), // -
    IXOR(130, StackPop(2), StackPush(1)), // -
    LXOR(131, StackPop(4), StackPush(2)), // -
    IINC(132), // visitIincInsn
    I2L(133, StackPush(1)), // visitInsn
    I2F(134), // -
    I2D(135, StackPush(1)), // -
    L2I(136, StackPop(1)), // -
    L2F(137, StackPop(1)), // -
    L2D(138), // -
    F2I(139), // -
    F2L(140, StackPush(1)), // -
    F2D(141, StackPush(1)), // -
    D2I(142, StackPop(1)), // -
    D2L(143), // -
    D2F(144, StackPop(1)), // -
    I2B(145), // -
    I2C(146), // -
    I2S(147), // -
    LCMP(148, StackPop(4), StackPush(1)), // -
    FCMPL(149, StackPop(2), StackPush(1)), // -
    FCMPG(150, StackPop(2), StackPush(1)), // -
    DCMPL(151, StackPop(4), StackPush(1)), // -
    DCMPG(152, StackPop(4), StackPush(1)), // -
    IFEQ(153, StackPop(1)), // visitJumpInsn
    IFNE(154, StackPop(1)), // -
    IFLT(155, StackPop(1)), // -
    IFGE(156, StackPop(1)), // -
    IFGT(157, StackPop(1)), // -
    IFLE(158, StackPop(1)), // -
    IF_ICMPEQ(159, StackPop(2)), // -
    IF_ICMPNE(160, StackPop(2)), // -
    IF_ICMPLT(161, StackPop(2)), // -
    IF_ICMPGE(162, StackPop(2)), // -
    IF_ICMPGT(163, StackPop(2)), // -
    IF_ICMPLE(164, StackPop(2)), // -
    IF_ACMPEQ(165, StackPop(2)), // -
    IF_ACMPNE(166, StackPop(2)), // -
    GOTO(167), // -
    JSR(168, StackPush(1)), // -
    RET(169, StackPush(2)), // visitVarInsn
    TABLESWITCH(170, StackPop(1)), // visiTableSwitchInsn
    LOOKUPSWITCH(171, StackPop(1)), // visitLookupSwitch
    IRETURN(172, StackPop(1)), // visitInsn
    LRETURN(173, StackPop(2)), // -
    FRETURN(174, StackPop(1)), // -
    DRETURN(175, StackPop(2)), // -
    ARETURN(176, StackPop(1)), // -
    RETURN(177), // -
    GETSTATIC(178, StackPush(1)), // visitFieldInsn
    PUTSTATIC(179, StackPop(1)), // -
    GETFIELD(180), // -
    PUTFIELD(181, StackPop(2)), // -
    INVOKEVIRTUAL(182), // visitMethodInsn
    INVOKESPECIAL(183), // -
    INVOKESTATIC(184), // -
    INVOKEINTERFACE(185), // -
    INVOKEDYNAMIC(186), // visitInvokeDynamicInsn
    NEW(187, StackPush(1)), // visitTypeInsn
    NEWARRAY(188), // visitIntInsn
    ANEWARRAY(189), // visitTypeInsn
    ARRAYLENGTH(190), // visitInsn
    ATHROW(191), // -
    CHECKCAST(192), // visitTypeInsn
    INSTANCEOF(193), // -
    MONITORENTER(194, StackPop(1)), // visitInsn
    MONITOREXIT(195, StackPop(1)), // -
    MULTIANEWARRAY(197), // visitMultiANewArrayInsn
    IFNULL(198, StackPop(1)), // visitJumpInsn
    IFNONNULL(199, StackPop(1)); // -

    val stackOps = ops
    companion object {
        fun toString(value: Int): String {
            return values().find { it.opcode == value }.toString()
        }
    }
}