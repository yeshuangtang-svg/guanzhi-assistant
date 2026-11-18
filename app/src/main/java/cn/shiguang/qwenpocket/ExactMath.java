package cn.shiguang.qwenpocket;
import java.math.*;
/** Decimal arithmetic parser; never evaluates Java, JavaScript or model code. */
final class ExactMath {
    final String input;int pos=0,depth=0;static final MathContext MC=new MathContext(30,RoundingMode.HALF_UP);
    ExactMath(String s){input=s.replace('×','*').replace('÷','/').replace('（','(').replace('）',')').replace("，","").replace(",","");}
    static String calculate(String expression){if(expression.length()>2000)throw new IllegalArgumentException("算式过长");ExactMath p=new ExactMath(expression);BigDecimal n=p.sum();p.space();if(p.pos!=p.input.length())throw new IllegalArgumentException("只支持数字、括号和 + - × ÷ ^");return n.stripTrailingZeros().toPlainString();}
    void space(){while(pos<input.length()&&Character.isWhitespace(input.charAt(pos)))pos++;}
    boolean eat(char c){space();if(pos<input.length()&&input.charAt(pos)==c){pos++;return true;}return false;}
    BigDecimal sum(){BigDecimal n=product();while(true){if(eat('+'))n=n.add(product());else if(eat('-'))n=n.subtract(product());else return n;}}
    BigDecimal product(){BigDecimal n=unary();while(true){if(eat('*'))n=n.multiply(unary());else if(eat('/'))n=n.divide(unary(),MC);else return n;}}
    BigDecimal unary(){if(++depth>64)throw new IllegalArgumentException("算式嵌套过深");try{if(eat('+'))return unary();if(eat('-'))return unary().negate();BigDecimal n=atom();if(eat('^')){int exponent=unary().intValueExact();if(exponent < -100||exponent >100)throw new IllegalArgumentException("指数须在 -100 到 100 之间");n=exponent>=0?n.pow(exponent,MC):BigDecimal.ONE.divide(n.pow(-exponent,MC),MC);}if(n.precision()>4000||Math.abs((long)n.scale())>4000)throw new IllegalArgumentException("结果超过计算范围");return n;}finally{depth--;}}
    BigDecimal atom(){space();if(eat('(')){BigDecimal n=sum();if(!eat(')'))throw new IllegalArgumentException("括号不匹配");return n;}int start=pos;while(pos<input.length()&&(Character.isDigit(input.charAt(pos))||input.charAt(pos)=='.'))pos++;if(start==pos)throw new IllegalArgumentException("缺少数字");String token=input.substring(start,pos);if(token.length()>100)throw new IllegalArgumentException("数字过长");return new BigDecimal(token);}
}
