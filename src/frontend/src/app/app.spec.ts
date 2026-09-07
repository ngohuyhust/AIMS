import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { Component } from '@angular/core';
import { provideRouter, Router } from '@angular/router';

@Component({ template: '<p>Order detail route</p>' })
class TestOrderPage {}

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([{ path: 'test-order', component: TestOrderPage }])],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('renders the selected route through the application shell', async () => {
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl('/test-order');
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('p')?.textContent).toBe('Order detail route');
  });
});
