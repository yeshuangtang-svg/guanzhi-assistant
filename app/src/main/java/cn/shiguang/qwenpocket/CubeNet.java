package cn.shiguang.qwenpocket;
import java.util.*;
/** Fold a user-entered square grid using integer orthogonal face bases. */
final class CubeNet {
    static int[] neg(int[] a){return new int[]{-a[0],-a[1],-a[2]};}
    static int[][][] fold(boolean[] grid){if(grid.length!=25)throw new IllegalArgumentException("需要 5×5 网格");int count=0,root=-1;for(int i=0;i<25;i++)if(grid[i]){count++;root=i;}if(count!=6)throw new IllegalArgumentException("请选中恰好六个面");int[][][] basis=new int[25][][];basis[root]=new int[][]{{1,0,0},{0,1,0},{0,0,1}};ArrayDeque<Integer> queue=new ArrayDeque<>();queue.add(root);int visited=0;Set<String> normals=new HashSet<>();while(!queue.isEmpty()){int i=queue.remove();visited++;int[][] b=basis[i];if(!normals.add(Arrays.toString(b[2])))throw new IllegalArgumentException("折叠后有面重叠，不能组成正方体");int x=i%5,y=i/5;for(int d=0;d<4;d++){int nx=x+(d==0?1:d==1?-1:0),ny=y+(d==2?1:d==3?-1:0);if(nx<0||nx>=5||ny<0||ny>=5)continue;int j=ny*5+nx;if(!grid[j])continue;int[][] next=d==0?new int[][]{neg(b[2]),b[1],b[0]}:d==1?new int[][]{b[2],b[1],neg(b[0])}:d==2?new int[][]{b[0],neg(b[2]),b[1]}:new int[][]{b[0],b[2],neg(b[1])};if(basis[j]==null){basis[j]=next;queue.add(j);}else if(!Arrays.deepEquals(basis[j],next))throw new IllegalArgumentException("面连接存在冲突，不能折成正方体");}}if(visited!=6)throw new IllegalArgumentException("六个面必须沿边连通");return basis;}
    static String describe(boolean[] grid){int[][][] b=fold(grid);StringBuilder out=new StringBuilder("手动确认的展开图相对面：\n");for(int i=0;i<25;i++)if(b[i]!=null)for(int j=i+1;j<25;j++)if(b[j]!=null&&Arrays.equals(neg(b[i][2]),b[j][2]))out.append(label(i)).append(" ↔ ").append(label(j)).append('\n');return out+"其余面相邻。字母/图案朝向与选项需继续核对。";}
    static String label(int i){return "("+(i/5+1)+","+(i%5+1)+")";}
}
