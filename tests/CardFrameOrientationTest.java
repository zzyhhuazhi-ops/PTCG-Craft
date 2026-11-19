package dev.hua.ptcglive.client;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import org.joml.Vector3f;

/** Exercises the renderer's real matrix; no graphics context or game installation required. */
public final class CardFrameOrientationTest {
    private static void near(Vector3f actual,Vector3f expected,String message) {
        if(actual.distance(expected)>0.00001f)
            throw new AssertionError(message+": "+actual+" != "+expected);
    }
    public static void main(String[] args) {
        int slots=0;
        for(var face:Direction.Type.HORIZONTAL) for(int size:new int[]{1,3,9,4}) {
            var matrices=new MatrixStack();
            CardFrameRenderer.orientFrame(matrices,face);
            var transform=matrices.peek().getPositionMatrix();
            var normal=new Vector3f(face.getOffsetX(),0,face.getOffsetZ());
            // Viewer-right is counterclockwise from the outward face, exactly as placement uses.
            var right=new Vector3f(normal.z,0,-normal.x);
            near(transform.transformDirection(new Vector3f(0,0,1)),normal,"Card front "+face);
            near(transform.transformDirection(new Vector3f(1,0,0)),right,"Column direction "+face);
            near(transform.transformDirection(new Vector3f(0,1,0)),new Vector3f(0,1,0),"Upright "+face);
            int columns=size==4?2:size==1?1:3,rows=size==4?2:size==9?3:1;
            if(size==4){
                float depth=-7f/16f+.001f;
                var center=transform.transformPosition(new Vector3f(.5f,.5f,depth));
                near(center,new Vector3f(.5f,1f,.5f).add(new Vector3f(right).mul(.5f)).add(new Vector3f(normal).mul(depth)),"Large single-card center "+face);
            }
            for(int row=0;row<rows;row++) for(int col=0;col<columns;col++) {
                var cell=new Vector3f(.5f,.5f+row,.5f).add(new Vector3f(right).mul(col));
                float depth=-7f/16f+.001f;
                var center=transform.transformPosition(new Vector3f(col,row,depth));
                near(center,new Vector3f(cell).add(new Vector3f(normal).mul(depth)),"Backing alignment "+face);
                for(float x:new float[]{-.3f,.3f}) for(float y:new float[]{-.42f,.42f}) {
                    var corner=transform.transformPosition(new Vector3f(col+x,row+y,depth));
                    near(corner,new Vector3f(center).add(new Vector3f(right).mul(x)).add(0,y,0),"Card bounds "+face);
                }
                slots++;
            }
            // The transparent cover uses this same transform and must stay over the whole frame.
            var cover=transform.transformPosition(new Vector3f(columns-1,rows-1,-.3115f));
            near(cover,new Vector3f(.5f,.5f+rows-1,.5f).add(new Vector3f(right).mul(columns-1))
                .add(new Vector3f(normal).mul(-.3115f)),"Glass alignment "+face);
        }
        System.out.println("CardFrameOrientationTest passed: 4 facings, 4 layouts, "+slots+" cells, large-card center/front/up/columns/card corners/glass.");
    }
}
